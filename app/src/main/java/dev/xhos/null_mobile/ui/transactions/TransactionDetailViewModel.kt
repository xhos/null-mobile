package dev.xhos.null_mobile.ui.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectrpc.Code
import com.connectrpc.fold
import com.google.protobuf.FieldMask
import com.google.type.Money
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.proto.Transaction
import dev.xhos.null_mobile.proto.TransactionDirection
import dev.xhos.null_mobile.proto.TransactionServiceClient
import dev.xhos.null_mobile.proto.deleteTransactionRequest
import dev.xhos.null_mobile.proto.getTransactionRequest
import dev.xhos.null_mobile.proto.updateTransactionRequest
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val apiClient: ApiClient,
    private val authManager: AuthManager,
    private val transactionId: Long,
) : ViewModel() {

    private val transactionService by lazy {
        TransactionServiceClient(apiClient.connectClient)
    }

    var transaction by mutableStateOf<Transaction?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isEditing by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var deleted by mutableStateOf(false)
        private set
    var wasEdited by mutableStateOf(false)
        private set

    // edit-mode fields
    var editAmount by mutableStateOf("")
    var editDirection by mutableStateOf(TransactionDirection.DIRECTION_OUTGOING)
    var editMerchant by mutableStateOf("")
    var editDescription by mutableStateOf("")
    var editNotes by mutableStateOf("")

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        val userId = authManager.userId ?: run {
            error = "user id not available"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = fetchTransaction(userId, retried = false)
            result.fold(
                onSuccess = { transaction = it },
                onFailure = { error = it.message },
            )
            isLoading = false
        }
    }

    fun startEditing() {
        val tx = transaction ?: return
        val money = tx.txAmount
        val value = money.units + money.nanos / 1_000_000_000.0
        editAmount = if (money.nanos == 0) money.units.toString() else String.format("%.2f", value)
        editDirection = tx.direction
        editMerchant = if (tx.hasMerchant()) tx.merchant else ""
        editDescription = if (tx.hasDescription()) tx.description else ""
        editNotes = if (tx.hasUserNotes()) tx.userNotes else ""
        isEditing = true
    }

    fun cancelEditing() {
        isEditing = false
    }

    fun saveEdit() {
        val tx = transaction ?: return
        val userId = authManager.userId ?: return
        if (isSaving) return

        viewModelScope.launch {
            isSaving = true
            error = null

            val result = performUpdate(tx, userId, retried = false)
            result.fold(
                onSuccess = {
                    isEditing = false
                    wasEdited = true
                    val refreshResult = fetchTransaction(userId, retried = false)
                    refreshResult.fold(
                        onSuccess = { transaction = it },
                        onFailure = { },
                    )
                },
                onFailure = { error = it.message },
            )
            isSaving = false
        }
    }

    fun deleteTransaction() {
        val userId = authManager.userId ?: return
        if (isDeleting) return

        viewModelScope.launch {
            isDeleting = true
            error = null

            val result = performDelete(userId, retried = false)
            result.fold(
                onSuccess = { deleted = true },
                onFailure = {
                    error = it.message
                    isDeleting = false
                },
            )
        }
    }

    private suspend fun fetchTransaction(
        userId: String,
        retried: Boolean,
    ): Result<Transaction> {
        return try {
            val request = getTransactionRequest {
                this.userId = userId
                this.id = transactionId
            }
            val response = transactionService.getTransaction(request)
            response.fold(
                onSuccess = { Result.success(it.transaction) },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return fetchTransaction(userId, retried = true)
                        }
                    }
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun performUpdate(
        tx: Transaction,
        userId: String,
        retried: Boolean,
    ): Result<Unit> {
        return try {
            val changedFields = mutableListOf<String>()

            val parsedAmount = editAmount.toDoubleOrNull()
            val oldValue = tx.txAmount.units + tx.txAmount.nanos / 1_000_000_000.0
            val amountChanged = parsedAmount != null && parsedAmount != oldValue
            if (amountChanged) changedFields.add("tx_amount")

            if (editDirection != tx.direction) changedFields.add("direction")
            if (editMerchant != (if (tx.hasMerchant()) tx.merchant else "")) changedFields.add("merchant")
            if (editDescription != (if (tx.hasDescription()) tx.description else "")) changedFields.add("description")
            if (editNotes != (if (tx.hasUserNotes()) tx.userNotes else "")) changedFields.add("user_notes")

            if (changedFields.isEmpty()) {
                isEditing = false
                return Result.success(Unit)
            }

            val request = updateTransactionRequest {
                this.userId = userId
                this.id = transactionId
                this.updateMask = FieldMask.newBuilder().addAllPaths(changedFields).build()

                if (amountChanged) {
                    val units = parsedAmount!!.toLong()
                    val nanos = ((parsedAmount - units) * 1_000_000_000).toInt()
                    this.txAmount = Money.newBuilder()
                        .setUnits(units)
                        .setNanos(nanos)
                        .setCurrencyCode(tx.txAmount.currencyCode)
                        .build()
                }
                if ("direction" in changedFields) this.direction = editDirection
                if ("merchant" in changedFields) this.merchant = editMerchant
                if ("description" in changedFields) this.description = editDescription
                if ("user_notes" in changedFields) this.userNotes = editNotes
            }

            val response = transactionService.updateTransaction(request)
            response.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return performUpdate(tx, userId, retried = true)
                        }
                    }
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun performDelete(
        userId: String,
        retried: Boolean,
    ): Result<Unit> {
        return try {
            val request = deleteTransactionRequest {
                this.userId = userId
                this.ids += transactionId
            }
            val response = transactionService.deleteTransaction(request)
            response.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return performDelete(userId, retried = true)
                        }
                    }
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
