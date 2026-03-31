package dev.xhos.null_mobile.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xhos.null_mobile.proto.Account
import dev.xhos.null_mobile.proto.Category
import dev.xhos.null_mobile.proto.TransactionDirection
import dev.xhos.null_mobile.ui.theme.ColorDestructive
import dev.xhos.null_mobile.ui.theme.ColorSuccess
import dev.xhos.null_mobile.ui.theme.ColorSuccessDark
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val commonCurrencies = listOf(
    "USD", "EUR", "GBP", "CAD", "JPY", "AUD", "CHF", "CNY", "KRW", "INR", "MXN"
)

private val currencySymbols = mapOf(
    "USD" to "$", "EUR" to "€", "GBP" to "£", "CAD" to "$", "JPY" to "¥",
    "AUD" to "$", "CHF" to "Fr", "CNY" to "¥", "KRW" to "₩", "INR" to "₹",
    "MXN" to "$",
)

private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    viewModel: QuickAddViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) {
            onSaved()
            delay(600)
            onClose()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.txDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        viewModel.setDate(date)
                    }
                    showDatePicker = false
                }) {
                    Text("ok", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = RoundedCornerShape(4.dp),
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.step != AddStep.DESCRIPTION) {
                IconButton(onClick = { viewModel.goBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "close",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            if (viewModel.step == AddStep.DESCRIPTION) {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = "pick date",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        when (viewModel.step) {
            AddStep.DESCRIPTION -> DescriptionStep(viewModel)
            AddStep.CATEGORY -> CategoryStep(viewModel)
            AddStep.ACCOUNT -> AccountStep(viewModel)
            AddStep.AMOUNT -> AmountStep(viewModel)
        }
    }
}

@Composable
private fun DescriptionStep(viewModel: QuickAddViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "add transaction",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (viewModel.txDate != LocalDate.now()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = viewModel.txDate.format(dateFormatter).lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        FormField(
            value = viewModel.description,
            onValueChange = { viewModel.description = it },
            label = "description",
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.nextStep() }),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.nextStep() },
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = "next", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryStep(viewModel: QuickAddViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "select category",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(viewModel.categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { viewModel.selectCategory(category) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = { viewModel.skipCategory() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "skip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "manage categories on the web app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
) {
    val dotColor = try {
        Color(android.graphics.Color.parseColor(category.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.slug.lowercase(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AccountStep(viewModel: QuickAddViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "select account",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(12.dp))

        viewModel.accounts.forEach { account ->
            AccountRow(
                account = account,
                onClick = { viewModel.selectAccount(account) },
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountRow(
    account: Account,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = account.name.lowercase(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = account.mainCurrency.lowercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmountStep(viewModel: QuickAddViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val isOutgoing = viewModel.direction == TransactionDirection.DIRECTION_OUTGOING
    val isDark = isSystemInDarkTheme()
    val directionColor = if (isOutgoing) ColorDestructive else if (isDark) ColorSuccessDark else ColorSuccess
    val currencyUpper = viewModel.currencyCode.uppercase()
    val symbol = currencySymbols[currencyUpper] ?: currencyUpper

    var showCurrencyMenu by remember { mutableStateOf(false) }
    val accountCurrency = viewModel.selectedAccount?.mainCurrency ?: ""
    val currencies = remember(accountCurrency) {
        buildList {
            if (accountCurrency.isNotEmpty()) add(accountCurrency.uppercase())
            commonCurrencies.forEach { if (!contains(it)) add(it) }
        }
    }

    val heroStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Direction toggle
        Text(
            text = if (isOutgoing) "expense" else "income",
            style = MaterialTheme.typography.labelLarge,
            color = directionColor,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { viewModel.toggleDirection() },
        )

        Spacer(Modifier.height(20.dp))

        // Amount area: currency at 25% from left, numbers at absolute center
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            // Currency symbol — centered in the left half (= 25% from left edge)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    style = heroStyle.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showCurrencyMenu = true },
                )
                DropdownMenu(
                    expanded = showCurrencyMenu,
                    onDismissRequest = { showCurrencyMenu = false },
                ) {
                    currencies.forEach { code ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = code.lowercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            onClick = {
                                viewModel.currencyCode = code
                                showCurrencyMenu = false
                            },
                        )
                    }
                }
            }

            // Number input — absolute center
            BasicTextField(
                value = viewModel.amount,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        viewModel.amount = value
                    }
                },
                textStyle = heroStyle.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                singleLine = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (viewModel.amount.isEmpty()) {
                            Text(
                                text = "0",
                                style = heroStyle.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Error
        if (viewModel.error != null) {
            Text(
                text = viewModel.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Saved feedback
        if (viewModel.saved) {
            Text(
                text = "saved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Save button
        Button(
            onClick = { viewModel.save() },
            enabled = !viewModel.isSaving && !viewModel.saved,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.outline,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = "add transaction",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    focusRequester: FocusRequester = remember { FocusRequester() },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            shape = RoundedCornerShape(4.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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
