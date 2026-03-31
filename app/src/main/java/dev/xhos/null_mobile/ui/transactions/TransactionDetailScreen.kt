package dev.xhos.null_mobile.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xhos.null_mobile.proto.Transaction
import dev.xhos.null_mobile.proto.TransactionDirection
import dev.xhos.null_mobile.ui.theme.ColorDestructive
import dev.xhos.null_mobile.ui.theme.ColorSuccess
import dev.xhos.null_mobile.ui.theme.ColorSuccessDark
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val detailTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, HH:mm")
private val detailTimeWithYearFormatter = DateTimeFormatter.ofPattern("MMMM d yyyy, HH:mm")
private val zone = ZoneId.systemDefault()

private val amountFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(viewModel.deleted) {
        if (viewModel.deleted) onBack()
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

        viewModel.error != null && viewModel.transaction == null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text(
                    text = "← back",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onBack() },
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = viewModel.error ?: "unknown error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        viewModel.transaction != null -> {
            if (viewModel.isEditing) {
                EditMode(viewModel = viewModel, onBack = onBack)
            } else {
                ViewMode(viewModel = viewModel, onBack = onBack)
            }
        }
    }
}

@Composable
private fun ViewMode(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
) {
    val tx = viewModel.transaction ?: return
    val isDark = isSystemInDarkTheme()
    val isIncoming = tx.direction == TransactionDirection.DIRECTION_INCOMING
    val amountColor = if (isIncoming) {
        if (isDark) ColorSuccessDark else ColorSuccess
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "← back",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onBack() },
            )
            Text(
                text = "edit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { viewModel.startEditing() },
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Spacer(Modifier.height(24.dp))

        // hero amount
        Text(
            text = formatDetailAmount(tx),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
            color = amountColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(4.dp))

        // direction + currency
        val dirLabel = if (isIncoming) "income" else "expense"
        val currency = tx.txAmount.currencyCode.lowercase()
        Text(
            text = "$dirLabel · $currency",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // field rows
        DetailRow("merchant", if (tx.hasMerchant() && tx.merchant.isNotBlank()) tx.merchant else "—")
        DetailRow("description", if (tx.hasDescription() && tx.description.isNotBlank()) tx.description else "—")
        DetailRow("account", if (tx.hasAccountName() && tx.accountName.isNotBlank()) tx.accountName else "—")
        if (tx.hasCategory()) {
            DetailRow("category", tx.category.slug)
        } else {
            DetailRow("category", "—")
        }
        DetailRow("notes", if (tx.hasUserNotes() && tx.userNotes.isNotBlank()) tx.userNotes else "—")

        Spacer(Modifier.height(8.dp))

        // timestamps
        DetailRow("created", formatDetailTimestamp(tx.createdAt.seconds, tx.createdAt.nanos.toLong()))
        DetailRow("updated", formatDetailTimestamp(tx.updatedAt.seconds, tx.updatedAt.nanos.toLong()))

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        // error
        if (viewModel.error != null) {
            Text(
                text = viewModel.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // delete button
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            enabled = !viewModel.isDeleting,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, ColorDestructive),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ColorDestructive,
            ),
            modifier = Modifier.fillMaxWidth().height(42.dp),
        ) {
            if (viewModel.isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ColorDestructive,
                )
            } else {
                Text(
                    text = "delete",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "delete this transaction?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteTransaction()
                    },
                ) {
                    Text(
                        text = "delete",
                        color = ColorDestructive,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = "cancel",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Composable
private fun EditMode(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
) {
    val tx = viewModel.transaction ?: return
    val isOutgoing = viewModel.editDirection == TransactionDirection.DIRECTION_OUTGOING
    val sign = if (isOutgoing) "-" else "+"
    val currencyUpper = tx.txAmount.currencyCode.uppercase()
    val symbol = try {
        Currency.getInstance(currencyUpper).symbol
    } catch (_: Exception) {
        currencyUpper
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "cancel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.cancelEditing() },
            )
            if (viewModel.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Text(
                    text = "save",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable { viewModel.saveEdit() },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Spacer(Modifier.height(24.dp))

        // direction toggle
        DirectionToggleInline(
            direction = viewModel.editDirection,
            onToggle = {
                viewModel.editDirection = if (viewModel.editDirection == TransactionDirection.DIRECTION_OUTGOING) {
                    TransactionDirection.DIRECTION_INCOMING
                } else {
                    TransactionDirection.DIRECTION_OUTGOING
                }
            },
        )

        Spacer(Modifier.height(20.dp))

        // hero amount editor
        BasicTextField(
            value = viewModel.editAmount,
            onValueChange = { value ->
                if (value.isEmpty() || value.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                    viewModel.editAmount = value
                }
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$sign$symbol",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            lineHeight = 40.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    innerTextField()
                }
            },
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        // editable fields
        EditFormField(
            value = viewModel.editMerchant,
            onValueChange = { viewModel.editMerchant = it },
            label = "merchant",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))

        EditFormField(
            value = viewModel.editDescription,
            onValueChange = { viewModel.editDescription = it },
            label = "description",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))

        EditFormField(
            value = viewModel.editNotes,
            onValueChange = { viewModel.editNotes = it },
            label = "notes",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(Modifier.height(16.dp))

        // read-only fields
        DetailRow("account", if (tx.hasAccountName() && tx.accountName.isNotBlank()) tx.accountName else "—")
        if (tx.hasCategory()) {
            DetailRow("category", tx.category.slug)
        } else {
            DetailRow("category", "—")
        }

        Spacer(Modifier.height(16.dp))

        // error
        if (viewModel.error != null) {
            Text(
                text = viewModel.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DirectionToggleInline(
    direction: TransactionDirection,
    onToggle: () -> Unit,
) {
    val isOutgoing = direction == TransactionDirection.DIRECTION_OUTGOING
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DirectionChipInline(
            label = "expense",
            selected = isOutgoing,
            onClick = { if (!isOutgoing) onToggle() },
        )
        DirectionChipInline(
            label = "income",
            selected = !isOutgoing,
            onClick = { if (isOutgoing) onToggle() },
        )
    }
}

@Composable
private fun DirectionChipInline(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun EditFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                cursorColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
    }
}

private fun formatDetailAmount(tx: Transaction): String {
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

private fun formatDetailTimestamp(seconds: Long, nanos: Long): String {
    val instant = Instant.ofEpochSecond(seconds, nanos)
    val zdt = instant.atZone(zone)
    val now = java.time.LocalDate.now()
    val formatter = if (zdt.year == now.year) detailTimeFormatter else detailTimeWithYearFormatter
    return zdt.format(formatter).lowercase()
}
