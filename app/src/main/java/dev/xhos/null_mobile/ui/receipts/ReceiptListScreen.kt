package dev.xhos.null_mobile.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xhos.null_mobile.proto.Receipt
import dev.xhos.null_mobile.proto.ReceiptStatus
import dev.xhos.null_mobile.ui.theme.ColorAccent
import dev.xhos.null_mobile.ui.theme.ColorDestructive
import dev.xhos.null_mobile.ui.theme.ColorSuccess
import dev.xhos.null_mobile.ui.theme.ColorSuccessDark
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable
fun ReceiptListScreen(
    viewModel: ReceiptListViewModel,
) {
    LaunchedEffect(Unit) {
        if (viewModel.receipts.isEmpty() && !viewModel.isLoading) {
            viewModel.loadReceipts()
        }
    }

    when {
        viewModel.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        viewModel.error != null && viewModel.receipts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = viewModel.error ?: "unknown error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        viewModel.receipts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "no receipts yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            ReceiptList(receipts = viewModel.receipts)
        }
    }
}

@Composable
private fun ReceiptList(receipts: List<Receipt>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(receipts, key = { it.id }) { receipt ->
            ReceiptRow(receipt = receipt)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt) {
    val isDark = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (receipt.hasMerchant()) receipt.merchant else "pending...",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (receipt.hasMerchant()) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = receipt.status, isDark = isDark)
                if (receipt.hasTotal()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "\u00b7",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatTotal(receipt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = formatDate(receipt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBadge(status: ReceiptStatus, isDark: Boolean) {
    val (label, bgColor, textColor) = when (status) {
        ReceiptStatus.RECEIPT_STATUS_PENDING -> Triple(
            "pending",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReceiptStatus.RECEIPT_STATUS_PARSED -> Triple(
            "parsed",
            ColorAccent.copy(alpha = 0.15f),
            ColorAccent,
        )
        ReceiptStatus.RECEIPT_STATUS_LINKED -> Triple(
            "linked",
            (if (isDark) ColorSuccessDark else ColorSuccess).copy(alpha = 0.15f),
            if (isDark) ColorSuccessDark else ColorSuccess,
        )
        ReceiptStatus.RECEIPT_STATUS_FAILED -> Triple(
            "failed",
            ColorDestructive.copy(alpha = 0.15f),
            ColorDestructive,
        )
        else -> Triple(
            "unknown",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d").withLocale(Locale.US)
private val zone = ZoneId.systemDefault()

private val amountFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun formatDate(receipt: Receipt): String {
    val instant = Instant.ofEpochSecond(receipt.createdAt.seconds, receipt.createdAt.nanos.toLong())
    return instant.atZone(zone).toLocalDate().format(dateFormatter).lowercase()
}

private fun formatTotal(receipt: Receipt): String {
    val money = receipt.total
    val value = money.units + money.nanos / 1_000_000_000.0
    val symbol = try {
        Currency.getInstance(money.currencyCode).symbol
    } catch (_: Exception) {
        money.currencyCode
    }
    return "$symbol${amountFormat.format(value)}"
}
