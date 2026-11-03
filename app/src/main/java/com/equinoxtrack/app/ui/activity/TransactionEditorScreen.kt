package com.equinoxtrack.app.ui.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.repo.LineDraft
import com.equinoxtrack.app.data.repo.TransactionDraft
import com.equinoxtrack.app.domain.AmountExpression
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.CurrencyCatalog
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.ConfirmDialog
import com.equinoxtrack.app.ui.components.EqDatePickerDialog
import com.equinoxtrack.app.ui.components.EqTextField
import com.equinoxtrack.app.ui.components.EqTimePickerDialog
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.PrimaryButton
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.Tag
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.Eq
import com.equinoxtrack.app.ui.theme.TabularNums
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class LineInput(val key: Int, val categoryId: Long?, val amountText: String)

data class EditorState(
    val loaded: Boolean = false,
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val expression: String = "",
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val toAmountText: String = "",
    val toAmountEdited: Boolean = false,
    val categoryId: Long? = null,
    val split: Boolean = false,
    val lines: List<LineInput> = emptyList(),
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val billId: Long? = null,
    val adjustmentNegative: Boolean = false,
)

class TransactionEditorViewModel(
    c: AppContainer,
    private val editId: Long,
    private val initialType: TxType,
    private val initialAccount: Long,
) : BaseViewModel(c) {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state
    private var nextKey = 1

    init {
        viewModelScope.launch {
            val l = ledger.filterNotNull().first()
            if (editId != 0L) {
                val t = l.data.transactions.firstOrNull { it.tx.id == editId }
                if (t == null) { message("This transaction no longer exists"); finish(); return@launch }
                val tx = t.tx
                val cur = l.currencyOf(tx.accountId)
                val dt = Periods.localDateTime(tx.occurredAt, l.zone)
                _state.value = EditorState(
                    loaded = true,
                    id = tx.id,
                    type = tx.type,
                    expression = Money.editable(kotlin.math.abs(tx.amountMinor), cur),
                    accountId = tx.accountId,
                    toAccountId = tx.toAccountId,
                    toAmountText = tx.toAmountMinor?.let { amt -> Money.editable(amt, tx.toAccountId?.let { l.currencyOf(it) } ?: cur) } ?: "",
                    toAmountEdited = tx.toAmountMinor != null && tx.toAmountMinor != tx.amountMinor,
                    categoryId = t.lines.singleOrNull()?.categoryId,
                    split = t.lines.size > 1,
                    lines = if (t.lines.size > 1) t.lines.map { LineInput(nextKey++, it.categoryId, Money.editable(it.amountMinor, cur)) } else emptyList(),
                    note = tx.note,
                    date = dt.toLocalDate(),
                    time = dt.toLocalTime().withSecond(0).withNano(0),
                    billId = tx.billId,
                    adjustmentNegative = tx.amountMinor < 0,
                )
            } else {
                val accounts = l.activeAccounts
                val account = accounts.firstOrNull { it.id == initialAccount } ?: accounts.firstOrNull()
                val other = accounts.firstOrNull { it.id != account?.id }
                _state.value = EditorState(
                    loaded = true,
                    type = initialType,
                    accountId = account?.id,
                    toAccountId = if (initialType == TxType.TRANSFER) other?.id else null,
                )
            }
        }
    }

    fun set(block: (EditorState) -> EditorState) = _state.update(block)

    fun press(key: Char, digits: Int) = _state.update { it.copy(expression = AmountExpression.press(it.expression, key, digits)) }
    fun backspace() = _state.update { it.copy(expression = it.expression.dropLast(1)) }
    fun evaluate(currency: String) = _state.update { s ->
        val v = AmountExpression.evaluate(s.expression) ?: return@update s
        s.copy(expression = Money.editable(Money.toMinor(v.abs(), currency), currency))
    }

    fun toggleSplit(currency: String) = _state.update { s ->
        if (s.split) s.copy(split = false, categoryId = s.lines.firstOrNull()?.categoryId ?: s.categoryId, lines = emptyList())
        else {
            val total = amountMinor(s, currency)
            s.copy(
                split = true,
                lines = listOf(
                    LineInput(nextKey++, s.categoryId, if (total > 0) Money.editable(total, currency) else ""),
                    LineInput(nextKey++, null, ""),
                ),
            )
        }
    }

    fun addLine() = _state.update { it.copy(lines = it.lines + LineInput(nextKey++, null, "")) }
    fun removeLine(key: Int) = _state.update { it.copy(lines = it.lines.filterNot { l -> l.key == key }) }
    fun updateLine(key: Int, block: (LineInput) -> LineInput) = _state.update { s -> s.copy(lines = s.lines.map { if (it.key == key) block(it) else it }) }

    fun amountMinor(s: EditorState, currency: String): Long =
        AmountExpression.evaluate(s.expression)?.let { Money.toMinor(it.abs(), currency) } ?: 0L

    fun save(l: Ledger) {
        val s = _state.value
        val account = s.accountId ?: run { message("Choose an account"); return }
        val cur = l.currencyOf(account)
        val amount = amountMinor(s, cur)
        val at = LocalDateTime.of(s.date, s.time).atZone(l.zone).toInstant().toEpochMilli()
        val draft = when (s.type) {
            TxType.TRANSFER -> {
                val to = s.toAccountId
                val toCur = to?.let { l.currencyOf(it) } ?: cur
                val typed = if (s.toAmountEdited) Money.parseToMinor(s.toAmountText, toCur) else null
                val toAmount = if (toCur == cur) amount else (typed ?: l.converter.convert(amount, cur, toCur) ?: 0L)
                TransactionDraft(id = s.id, type = TxType.TRANSFER, accountId = account, toAccountId = to, amountMinor = amount, toAmountMinor = toAmount, note = s.note, occurredAt = at)
            }
            TxType.ADJUSTMENT -> TransactionDraft(id = s.id, type = TxType.ADJUSTMENT, accountId = account, amountMinor = if (s.adjustmentNegative) -amount else amount, note = s.note, occurredAt = at)
            else -> {
                val lines = if (s.split) s.lines.map { LineDraft(it.categoryId, Money.parseToMinor(it.amountText, cur) ?: 0L) }
                else if (s.categoryId != null) listOf(LineDraft(s.categoryId, amount)) else emptyList()
                TransactionDraft(id = s.id, type = s.type, accountId = account, amountMinor = amount, note = s.note, occurredAt = at, lines = lines, billId = s.billId)
            }
        }
        act(success = if (s.id == 0L) "Saved" else "Updated", close = true) { container.repository.saveTransaction(draft) }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L) return
        act(success = "Transaction deleted", close = true) { container.repository.deleteTransaction(id) }
    }
}

@Composable
fun TransactionEditorScreen(
    editId: Long,
    initialType: TxType,
    initialAccount: Long,
    onClose: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val vm = appViewModel(key = "tx-$editId-$initialType-$initialAccount") { TransactionEditorViewModel(it, editId, initialType, initialAccount) }
    VmEffects(vm, onDone = onClose)
    val s by vm.state.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    if (!s.loaded) return
    val c = Eq.colors

    val account = s.accountId?.let { l.accountsById[it] }
    val toAccount = s.toAccountId?.let { l.accountsById[it] }
    val currency = account?.currency ?: l.base
    val digits = CurrencyCatalog.digits(currency)
    val amount = vm.amountMinor(s, currency)
    val typeColor = when (s.type) {
        TxType.INCOME -> c.income
        TxType.EXPENSE -> c.expense
        TxType.TRANSFER -> c.transfer
        TxType.ADJUSTMENT -> c.textSecondary
    }

    var keypad by remember { mutableStateOf(editId == 0L) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var splitPickerKey by remember { mutableStateOf<Int?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (l.activeAccounts.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            EqTopBar("New transaction", onBack = onClose)
            com.equinoxtrack.app.ui.components.EmptyState(
                Icons.Rounded.Add, "Add an account first", "Transactions belong to an account such as Cash, a bank or an e-wallet.",
                action = "Create account", onAction = onCreateAccount,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val title = when {
            s.type == TxType.ADJUSTMENT -> "Balance adjustment"
            s.id == 0L -> "New ${s.type.name.lowercase()}"
            else -> "Edit ${s.type.name.lowercase()}"
        }
        EqTopBar(title, onBack = onClose) {
            if (s.id != 0L) CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }, tint = c.expense)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s.type != TxType.ADJUSTMENT) {
                if (s.billId != null) {
                    Tag("Bill payment · linked to ${l.data.bills.firstOrNull { it.id == s.billId }?.name ?: "a bill"}", c.warning)
                } else {
                    SegmentedControl(
                        options = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER),
                        selected = s.type,
                        onSelect = { t ->
                            vm.set {
                                val other = l.activeAccounts.firstOrNull { a -> a.id != it.accountId }?.id
                                it.copy(type = t, categoryId = null, split = false, lines = emptyList(), toAccountId = if (t == TxType.TRANSFER) (it.toAccountId ?: other) else null)
                            }
                        },
                        label = { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } },
                        colorFor = { t -> if (t == TxType.INCOME) c.income else if (t == TxType.EXPENSE) c.expense else c.transfer },
                    )
                }
            } else {
                SegmentedControl(
                    options = listOf(false, true),
                    selected = s.adjustmentNegative,
                    onSelect = { neg -> vm.set { it.copy(adjustmentNegative = neg) } },
                    label = { if (it) "Decrease" else "Increase" },
                )
            }

            // Amount display
            Column(
                Modifier.fillMaxWidth()
                    .pressable({ keypad = true }, RoundedCornerShape(24.dp))
                    .background(c.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, if (keypad) typeColor.copy(alpha = 0.5f) else c.stroke, RoundedCornerShape(24.dp))
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Overline(if (s.type == TxType.TRANSFER) "Send" else "Amount")
                Spacer(Modifier.height(4.dp))
                val symbol = CurrencyCatalog.info(currency).symbol
                Text(
                    "$symbol ${s.expression.ifEmpty { "0" }}",
                    style = Eq.type.displayMedium.copy(fontFeatureSettings = TabularNums),
                    color = if (s.expression.isEmpty()) c.textTertiary else c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (AmountExpression.hasOperator(s.expression)) {
                    Text("= ${Money.format(amount, currency)}", style = Eq.type.titleMedium, color = typeColor)
                }
            }

            // Accounts
            AccountSelector(if (s.type == TxType.TRANSFER) "From" else "Account", account, l) { sheet = "from" }
            if (s.type == TxType.TRANSFER) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircleIconButton(Icons.Rounded.SwapVert, "Swap accounts", { vm.set { it.copy(accountId = it.toAccountId, toAccountId = it.accountId, toAmountEdited = false, toAmountText = "") } }, size = 36.dp)
                }
                AccountSelector("To", toAccount, l) { sheet = "to" }
                val toCur = toAccount?.currency
                if (toCur != null && toCur != currency) {
                    val suggested = l.converter.convert(amount, currency, toCur)
                    val shown = if (s.toAmountEdited) s.toAmountText else suggested?.let { Money.editable(it, toCur) } ?: ""
                    EqTextField(
                        value = shown,
                        onValueChange = { v -> vm.set { it.copy(toAmountText = v.filter { ch -> ch.isDigit() || ch == '.' }, toAmountEdited = true) } },
                        label = "Amount received ($toCur)",
                        keyboardType = KeyboardType.Decimal,
                        supporting = if (suggested == null) "No exchange rate set — enter the amount received" else "Suggested from your exchange rates; edit if different",
                    )
                }
            }

            // Categories
            if (s.type == TxType.EXPENSE || s.type == TxType.INCOME) {
                val kind = if (s.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val cats = l.data.categories.filter { it.kind == kind && (!it.archived || it.id == s.categoryId) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Category", Modifier.weight(1f))
                    if (s.type == TxType.EXPENSE && s.billId == null) {
                        ChoiceChip("Split", s.split, { vm.toggleSplit(currency) }, icon = Icons.Rounded.CallSplit)
                    }
                }
                if (!s.split) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.forEach { cat ->
                            ChoiceChip(cat.name, s.categoryId == cat.id, { vm.set { it.copy(categoryId = cat.id) } }, icon = AppIcons.of(cat.icon), tint = Color(cat.color))
                        }
                    }
                } else {
                    val allocated = s.lines.sumOf { Money.parseToMinor(it.amountText, currency) ?: 0L }
                    val remaining = amount - allocated
                    s.lines.forEach { line ->
                        val cat = line.categoryId?.let { l.categoriesById[it] }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).pressable({ splitPickerKey = line.key }, RoundedCornerShape(16.dp))
                                    .background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.stroke, RoundedCornerShape(16.dp)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(AppIcons.of(cat?.icon), cat?.let { Color(it.color) } ?: c.textTertiary, size = 32.dp, corner = 10.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(cat?.name ?: "Choose", style = Eq.type.labelLarge, color = if (cat == null) c.textTertiary else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textTertiary)
                            }
                            Spacer(Modifier.width(8.dp))
                            EqTextField(
                                value = line.amountText,
                                onValueChange = { v -> vm.updateLine(line.key) { it.copy(amountText = v.filter { ch -> ch.isDigit() || ch == '.' }) } },
                                label = "Amount",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.width(120.dp).onFocusChanged { if (it.isFocused) keypad = false },
                            )
                            if (s.lines.size > 2) {
                                CircleIconButton(Icons.Rounded.Close, "Remove", { vm.removeLine(line.key) }, size = 34.dp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChip("Add split", false, { vm.addLine() }, icon = Icons.Rounded.Add)
                        Spacer(Modifier.weight(1f))
                        if (remaining != 0L) {
                            Text(
                                (if (remaining > 0) "Unassigned " else "Over by ") + Money.format(kotlin.math.abs(remaining), currency),
                                style = Eq.type.labelMedium, color = if (remaining > 0) c.warning else c.expense,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable({
                                    val last = s.lines.lastOrNull() ?: return@pressable
                                    val lastAmount = Money.parseToMinor(last.amountText, currency) ?: 0L
                                    val fixed = (lastAmount + remaining).coerceAtLeast(0)
                                    vm.updateLine(last.key) { it.copy(amountText = Money.editable(fixed, currency)) }
                                }, RoundedCornerShape(8.dp)).padding(6.dp),
                            )
                        } else Tag("Balanced", c.income)
                    }
                }
            }

            // Date & time
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(Fmt.dayHeader(s.date, LocalDate.now()), false, { showDate = true }, icon = Icons.Rounded.DateRange)
                ChoiceChip(Fmt.time(s.time), false, { showTime = true }, icon = Icons.Rounded.Schedule)
            }

            EqTextField(
                value = s.note,
                onValueChange = { v -> vm.set { it.copy(note = v.take(200)) } },
                label = "Note",
                placeholder = "What was it for?",
                modifier = Modifier.onFocusChanged { if (it.isFocused) keypad = false },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Keypad & save
        Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            AnimatedVisibility(keypad, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Keypad(
                    onKey = { vm.press(it, digits) },
                    onBackspace = { vm.backspace() },
                    allowDecimal = digits > 0,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!keypad) {
                    ChoiceChip("Keypad", false, { keypad = true })
                    Spacer(Modifier.width(10.dp))
                }
                val needsEval = AmountExpression.hasOperator(s.expression)
                PrimaryButton(
                    text = if (needsEval) "= Calculate" else "Save",
                    onClick = { if (needsEval) vm.evaluate(currency) else vm.save(l) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    when (sheet) {
        "from", "to" -> PickerSheet(
            title = if (sheet == "to") "To account" else "Account",
            items = l.activeAccounts.map { a -> PickerItem(a.id, a.name, Money.format(l.balance(a.id), a.currency), AppIcons.of(a.icon), Color(a.color)) },
            selected = if (sheet == "to") s.toAccountId else s.accountId,
            onSelect = { id -> if (sheet == "to") vm.set { it.copy(toAccountId = id, toAmountEdited = false) } else vm.set { it.copy(accountId = id, toAmountEdited = false) } },
            onDismiss = { sheet = null },
        )
    }
    splitPickerKey?.let { key ->
        val kind = if (s.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        PickerSheet(
            title = "Category",
            items = l.data.categories.filter { it.kind == kind && !it.archived }.map { PickerItem<Long?>(it.id, it.name, null, AppIcons.of(it.icon), Color(it.color)) },
            selected = s.lines.firstOrNull { it.key == key }?.categoryId,
            onSelect = { id -> vm.updateLine(key) { it.copy(categoryId = id) } },
            onDismiss = { splitPickerKey = null },
            searchable = true,
        )
    }
    if (showDate) EqDatePickerDialog(s.date, { showDate = false }) { d -> vm.set { it.copy(date = d) } }
    if (showTime) EqTimePickerDialog(s.time, { showTime = false }) { t -> vm.set { it.copy(time = t) } }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete transaction?",
            if (s.billId != null) "The linked bill occurrence will be marked unpaid again and the amount returns to the account."
            else "Account balances will be restored as if it never happened.",
            "Delete", { vm.delete() }, { confirmDelete = false },
        )
    }
}

@Composable
private fun AccountSelector(label: String, account: AccountEntity?, l: Ledger, onClick: () -> Unit) {
    val c = Eq.colors
    Row(
        Modifier.fillMaxWidth()
            .pressable(onClick, RoundedCornerShape(20.dp))
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.stroke, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(AppIcons.of(account?.icon), account?.let { Color(it.color) } ?: c.textTertiary, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Eq.type.labelMedium, color = c.textTertiary)
            Text(account?.name ?: "Choose account", style = Eq.type.titleSmall, color = c.textPrimary)
        }
        if (account != null) Text(Money.format(l.balance(account.id), account.currency), style = Eq.type.labelLarge, color = c.textSecondary)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textTertiary)
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, allowDecimal: Boolean) {
    val rows = listOf(
        listOf('7', '8', '9', '÷'),
        listOf('4', '5', '6', '×'),
        listOf('1', '2', '3', '-'),
        listOf('.', '0', '<', '+'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isOp = AmountExpression.isOperator(key)
                    when (key) {
                        '<' -> KeyButton(icon = Icons.Rounded.Backspace, modifier = Modifier.weight(1f), onClick = onBackspace)
                        '.' -> KeyButton(text = if (allowDecimal) "." else "", modifier = Modifier.weight(1f), onClick = { if (allowDecimal) onKey('.') })
                        else -> KeyButton(text = if (key == '-') "−" else key.toString(), operator = isOp, modifier = Modifier.weight(1f), onClick = { onKey(key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(modifier: Modifier, onClick: () -> Unit, text: String = "", icon: ImageVector? = null, operator: Boolean = false) {
    val c = Eq.colors
    Box(
        modifier
            .height(52.dp)
            .pressable(onClick, RoundedCornerShape(16.dp))
            .background(if (operator) c.accentSoft else c.surfaceRaised, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, "Backspace", tint = c.textPrimary, modifier = Modifier.size(22.dp))
        else Text(text, style = Eq.type.headlineSmall, color = if (operator) c.accent else c.textPrimary)
    }
}
