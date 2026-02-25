package dev.xhos.null_mobile.ui.transactions

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
import dev.xhos.null_mobile.proto.Cursor
import dev.xhos.null_mobile.proto.Transaction
import dev.xhos.null_mobile.proto.TransactionServiceClient
import dev.xhos.null_mobile.proto.listTransactionsRequest
import kotlinx.coroutines.launch

class TransactionListViewModel(
    private val apiClient: ApiClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val transactionService by lazy {
        TransactionServiceClient(apiClient.connectClient)
    }

    var transactions by mutableStateOf<List<Transaction>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var totalCount by mutableLongStateOf(0L)
        private set

    private var nextCursor: Cursor? = null
    private var hasMore = true

    fun loadTransactions() {
        if (isLoading) return
        val userId = authManager.userId ?: run {
            error = "user id not available"
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null

            val result = fetchTransactions(userId, cursor = null)
            result.fold(
                onSuccess = { (items, count, cursor) ->
                    transactions = items
                    totalCount = count
                    nextCursor = cursor
                    hasMore = cursor != null
                },
                onFailure = { error = it.message }
            )

            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore || isLoading || !hasMore) return
        val userId = authManager.userId ?: return
        val cursor = nextCursor ?: return

        viewModelScope.launch {
            isLoadingMore = true

            val result = fetchTransactions(userId, cursor)
            result.fold(
                onSuccess = { (items, count, newCursor) ->
                    transactions = transactions + items
                    totalCount = count
                    nextCursor = newCursor
                    hasMore = newCursor != null
                },
                onFailure = { error = it.message }
            )

            isLoadingMore = false
        }
    }

    fun refresh() {
        nextCursor = null
        hasMore = true
        loadTransactions()
    }

    private suspend fun fetchTransactions(
        userId: String,
        cursor: Cursor?,
        retried: Boolean = false,
    ): Result<Triple<List<Transaction>, Long, Cursor?>> {
        return try {
            val request = listTransactionsRequest {
                this.userId = userId
                this.limit = PAGE_SIZE
                if (cursor != null) this.cursor = cursor
            }

            val response = transactionService.listTransactions(request)
            response.fold(
                onSuccess = { message ->
                    val nextCur = if (message.hasNextCursor()) message.nextCursor else null
                    Result.success(Triple(message.transactionsList, message.totalCount, nextCur))
                },
                onFailure = { connectException ->
                    if (connectException.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return fetchTransactions(userId, cursor, retried = true)
                        }
                    }
                    Result.failure(connectException)
                },
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
