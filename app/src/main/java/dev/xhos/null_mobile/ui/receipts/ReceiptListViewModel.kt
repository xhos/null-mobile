package dev.xhos.null_mobile.ui.receipts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectrpc.Code
import com.connectrpc.fold
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.proto.Receipt
import dev.xhos.null_mobile.proto.ReceiptServiceClient
import dev.xhos.null_mobile.proto.listReceiptsRequest
import kotlinx.coroutines.launch

class ReceiptListViewModel(
    private val apiClient: ApiClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val receiptService by lazy { ReceiptServiceClient(apiClient.connectClient) }

    var receipts by mutableStateOf<List<Receipt>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var totalCount by mutableLongStateOf(0L)
        private set

    fun loadReceipts() {
        if (isLoading) return
        val userId = authManager.userId ?: run {
            error = "user id not available"
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null

            val result = fetchReceipts(userId, retried = false)
            result.fold(
                onSuccess = { (items, count) ->
                    receipts = items
                    totalCount = count
                },
                onFailure = { error = it.message },
            )

            isLoading = false
        }
    }

    fun refresh() {
        loadReceipts()
    }

    private suspend fun fetchReceipts(
        userId: String,
        retried: Boolean,
    ): Result<Pair<List<Receipt>, Long>> {
        return try {
            val request = listReceiptsRequest {
                this.userId = userId
                this.limit = 30
            }

            val response = receiptService.listReceipts(request)
            response.fold(
                onSuccess = { msg ->
                    Result.success(Pair(msg.receiptsList, msg.totalCount))
                },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return fetchReceipts(userId, retried = true)
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
