package dev.xhos.null_mobile.ui.transactions

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xhos.null_mobile.proto.Transaction
import dev.xhos.null_mobile.proto.TransactionDirection
import dev.xhos.null_mobile.ui.theme.ColorSuccess
import dev.xhos.null_mobile.ui.theme.ColorSuccessDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
) {
    LaunchedEffect(Unit) {
        if (viewModel.transactions.isEmpty() && !viewModel.isLoading) {
            viewModel.loadTransactions()
        }
    }

    val listState = rememberLazyListState()

    // Pagination: snapshotFlow only collects when the flow value changes,
    // so loadMore won't spam during fast flings.
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
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

        viewModel.error != null && viewModel.transactions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = viewModel.error ?: "unknown error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        viewModel.transactions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "no transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> {
            TransactionList(
                transactions = viewModel.transactions,
                isLoadingMore = viewModel.isLoadingMore,
                listState = listState,
            )
        }
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    isLoadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val isDark = isSystemInDarkTheme()
    val incomingColor = if (isDark) ColorSuccessDark else ColorSuccess

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        var lastDateLabel: String? = null

        transactions.forEach { tx ->
            val dateLabel = formatDateLabel(tx)

            if (dateLabel != lastDateLabel) {
                lastDateLabel = dateLabel
                item(key = "header_$dateLabel") {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item(key = tx.id) {
                TransactionRow(tx = tx, incomingColor = incomingColor)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, incomingColor: Color) {
    val isIncoming = tx.direction == TransactionDirection.DIRECTION_INCOMING
    val amountColor = if (isIncoming) incomingColor else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.displayTitle(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tx.hasCategory()) {
                    CategoryBadge(slug = tx.category.slug, colorHex = tx.category.color)
                    if (tx.hasAccountName()) {
                        Spacer(Modifier.width(6.dp))
                        Text("\u00b7", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (tx.hasAccountName()) {
                    Text(
                        text = tx.accountName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmount(tx),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = amountColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatTime(tx),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryBadge(slug: String, colorHex: String) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        Color.Gray
    }
    val luminance = 0.299 * bgColor.red + 0.587 * bgColor.green + 0.114 * bgColor.blue
    val textColor = if (luminance > 0.5) Color.Black else Color.White

    Text(
        text = slug.substringAfterLast("."),
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

// --- Formatting helpers ---

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE")
private val monthDayFormatter = DateTimeFormatter.ofPattern("MMMM d")
private val fullDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val zone = ZoneId.systemDefault()

private val amountFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun Transaction.displayTitle(): String = when {
    hasMerchant() && merchant.isNotBlank() -> merchant
    hasDescription() && description.isNotBlank() -> description
    else -> "untitled"
}

private fun formatAmount(tx: Transaction): String {
    val money = tx.txAmount
    val value = money.units + money.nanos / 1_000_000_000.0
    val symbol = try {
        Currency.getInstance(money.currencyCode).symbol
    } catch (_: Exception) {
        money.currencyCode
    }
    val sign = if (tx.direction == TransactionDirection.DIRECTION_INCOMING) "+" else "-"
    return "$sign$symbol${amountFormat.format(value)}"
}

private fun formatTime(tx: Transaction): String {
    val instant = Instant.ofEpochSecond(tx.txDate.seconds, tx.txDate.nanos.toLong())
    return instant.atZone(zone).toLocalTime().format(timeFormatter)
}

private fun formatDateLabel(tx: Transaction): String {
    val instant = Instant.ofEpochSecond(tx.txDate.seconds, tx.txDate.nanos.toLong())
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "today"
        date == today.minusDays(1) -> "yesterday"
        date.isAfter(today.minusDays(7)) -> date.format(weekdayFormatter).lowercase()
        date.year == today.year -> date.format(monthDayFormatter).lowercase()
        else -> date.format(fullDateFormatter).lowercase()
    }
}
