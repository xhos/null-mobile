package dev.xhos.null_mobile.ui.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectrpc.Code
import com.connectrpc.fold
import com.google.protobuf.Timestamp
import com.google.type.Money
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.proto.Account
import dev.xhos.null_mobile.proto.AccountServiceClient
import dev.xhos.null_mobile.proto.Category
import dev.xhos.null_mobile.proto.CategoryServiceClient
import dev.xhos.null_mobile.proto.TransactionDirection
import dev.xhos.null_mobile.proto.TransactionServiceClient
import dev.xhos.null_mobile.proto.createTransactionRequest
import dev.xhos.null_mobile.proto.listAccountsRequest
import dev.xhos.null_mobile.proto.listCategoriesRequest
import dev.xhos.null_mobile.proto.transactionInput
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class AddStep { DESCRIPTION, CATEGORY, ACCOUNT, AMOUNT }

class QuickAddViewModel(
    private val apiClient: ApiClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val accountService by lazy { AccountServiceClient(apiClient.connectClient) }
    private val transactionService by lazy { TransactionServiceClient(apiClient.connectClient) }
    private val categoryService by lazy { CategoryServiceClient(apiClient.connectClient) }

    var step by mutableStateOf(AddStep.DESCRIPTION)
        private set
    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set
    var selectedAccount by mutableStateOf<Account?>(null)
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var selectedCategory by mutableStateOf<Category?>(null)
        private set
    var txDate by mutableStateOf(LocalDate.now())
        private set
    var amount by mutableStateOf("")
    var direction by mutableStateOf(TransactionDirection.DIRECTION_OUTGOING)
    var description by mutableStateOf("")
    var currencyCode by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set

    fun loadAccounts() {
        val userId = authManager.userId ?: return
        viewModelScope.launch {
            val request = listAccountsRequest { this.userId = userId }
            val response = accountService.listAccounts(request)
            response.fold(
                onSuccess = { msg ->
                    accounts = msg.accountsList
                    if (msg.accountsList.size == 1) {
                        selectedAccount = msg.accountsList.first()
                        currencyCode = msg.accountsList.first().mainCurrency
                    }
                },
                onFailure = { error = it.message },
            )
        }
    }

    fun loadCategories() {
        val userId = authManager.userId ?: return
        viewModelScope.launch {
            val request = listCategoriesRequest {
                this.userId = userId
                this.limit = 100
            }
            val response = categoryService.listCategories(request)
            response.fold(
                onSuccess = { msg -> categories = msg.categoriesList },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            loadCategories()
                            return@fold
                        }
                    }
                    error = e.message
                },
            )
        }
    }

    fun selectCategory(category: Category) {
        selectedCategory = category
        advanceFromCategory()
    }

    fun skipCategory() {
        selectedCategory = null
        advanceFromCategory()
    }

    private fun advanceFromCategory() {
        if (selectedAccount != null && accounts.size == 1) {
            step = AddStep.AMOUNT
        } else {
            step = AddStep.ACCOUNT
        }
    }

    fun selectAccount(account: Account) {
        selectedAccount = account
        currencyCode = account.mainCurrency
        step = AddStep.AMOUNT
    }

    fun setDate(date: LocalDate) {
        txDate = date
    }

    fun toggleDirection() {
        direction = if (direction == TransactionDirection.DIRECTION_OUTGOING) {
            TransactionDirection.DIRECTION_INCOMING
        } else {
            TransactionDirection.DIRECTION_OUTGOING
        }
    }

    fun goBack() {
        when (step) {
            AddStep.CATEGORY -> step = AddStep.DESCRIPTION
            AddStep.ACCOUNT -> step = AddStep.CATEGORY
            AddStep.AMOUNT -> {
                if (selectedAccount != null && accounts.size == 1) {
                    step = AddStep.CATEGORY
                } else {
                    step = AddStep.ACCOUNT
                }
            }
            else -> {}
        }
    }

    fun nextStep() {
        when (step) {
            AddStep.DESCRIPTION -> step = AddStep.CATEGORY
            else -> {}
        }
    }

    fun save() {
        if (isSaving) return

        val account = selectedAccount ?: run {
            error = "select an account"
            return
        }

        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0) {
            error = "enter a valid amount"
            return
        }

        val userId = authManager.userId ?: run {
            error = "not authenticated"
            return
        }

        viewModelScope.launch {
            isSaving = true
            error = null

            val result = createTransaction(userId, account, parsedAmount, retried = false)
            result.fold(
                onSuccess = { saved = true },
                onFailure = { error = it.message },
            )

            isSaving = false
        }
    }

    fun resetFlow() {
        step = AddStep.DESCRIPTION
        amount = ""
        description = ""
        selectedCategory = null
        txDate = LocalDate.now()
        direction = TransactionDirection.DIRECTION_OUTGOING
        error = null
        saved = false
        if (accounts.size != 1) {
            selectedAccount = null
            currencyCode = ""
        }
    }

    private suspend fun createTransaction(
        userId: String,
        account: Account,
        parsedAmount: Double,
        retried: Boolean,
    ): Result<Unit> {
        return try {
            val zonedDateTime = txDate.atStartOfDay(ZoneId.systemDefault())
            val instant = zonedDateTime.toInstant()
            val units = parsedAmount.toLong()
            val nanos = ((parsedAmount - units) * 1_000_000_000).toInt()

            val request = createTransactionRequest {
                this.userId = userId
                this.transactions.add(transactionInput {
                    this.accountId = account.id
                    this.txDate = Timestamp.newBuilder()
                        .setSeconds(instant.epochSecond)
                        .setNanos(instant.nano)
                        .build()
                    this.txAmount = Money.newBuilder()
                        .setUnits(units)
                        .setNanos(nanos)
                        .setCurrencyCode(this@QuickAddViewModel.currencyCode.ifEmpty { account.mainCurrency })
                        .build()
                    this.direction = this@QuickAddViewModel.direction
                    if (this@QuickAddViewModel.description.isNotBlank()) {
                        this.description = this@QuickAddViewModel.description
                    }
                    val cat = this@QuickAddViewModel.selectedCategory
                    if (cat != null) {
                        this.categoryId = cat.id
                    }
                })
            }

            val response = transactionService.createTransaction(request)
            response.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return createTransaction(userId, account, parsedAmount, retried = true)
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
