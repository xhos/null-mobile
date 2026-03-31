package dev.xhos.null_mobile.ui.receipts

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectrpc.Code
import com.connectrpc.fold
import com.google.protobuf.ByteString
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.proto.Receipt
import dev.xhos.null_mobile.proto.ReceiptServiceClient
import dev.xhos.null_mobile.proto.uploadReceiptRequest
import kotlinx.coroutines.launch

class ReceiptCaptureViewModel(
    private val apiClient: ApiClient,
    private val authManager: AuthManager,
) : ViewModel() {

    private val receiptService by lazy { ReceiptServiceClient(apiClient.connectClient) }

    var capturedImageUri by mutableStateOf<Uri?>(null)
        private set
    var isUploading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var uploadedReceipt by mutableStateOf<Receipt?>(null)
        private set

    fun setCapturedUri(uri: Uri) {
        capturedImageUri = uri
        error = null
        uploadedReceipt = null
    }

    fun upload(context: Context) {
        val uri = capturedImageUri ?: return
        val userId = authManager.userId ?: run {
            error = "not authenticated"
            return
        }

        viewModelScope.launch {
            isUploading = true
            error = null

            val result = performUpload(context, uri, userId, retried = false)
            result.fold(
                onSuccess = { uploadedReceipt = it },
                onFailure = { error = it.message },
            )

            isUploading = false
        }
    }

    fun reset() {
        capturedImageUri = null
        isUploading = false
        error = null
        uploadedReceipt = null
    }

    private suspend fun performUpload(
        context: Context,
        uri: Uri,
        userId: String,
        retried: Boolean,
    ): Result<Receipt> {
        return try {
            val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(Exception("failed to read image"))

            val request = uploadReceiptRequest {
                this.userId = userId
                this.imageData = ByteString.copyFrom(bytes)
                this.contentType = contentType
            }

            val response = receiptService.uploadReceipt(request)
            response.fold(
                onSuccess = { msg -> Result.success(msg.receipt) },
                onFailure = { e ->
                    if (e.code == Code.UNAUTHENTICATED && !retried) {
                        val refreshResult = authManager.refreshToken()
                        if (refreshResult.isSuccess) {
                            return performUpload(context, uri, userId, retried = true)
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
