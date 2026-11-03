package com.equinoxtrack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.domain.BillFrequency
import com.equinoxtrack.app.domain.BillSchedule
import com.equinoxtrack.app.domain.BudgetPeriod
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.CurrencyCatalog
import com.equinoxtrack.app.domain.Defaults
import com.equinoxtrack.app.domain.GoalCalculator
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.ConfirmDialog
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.EqDatePickerDialog
import com.equinoxtrack.app.ui.components.EqSwitch
import com.equinoxtrack.app.ui.components.EqTextField
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.HeroSurface
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.IconPicker
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.PrimaryButton
import com.equinoxtrack.app.ui.components.RingProgress
import com.equinoxtrack.app.ui.components.SecondaryButton
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.SettingRow
import com.equinoxtrack.app.ui.components.SwatchPicker
import com.equinoxtrack.app.ui.components.Tag
import com.equinoxtrack.app.ui.theme.Eq
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PlanEditViewModel(c: AppContainer) : BaseViewModel(c) {
    fun saveBudget(b: BudgetEntity) = act(if (b.id == 0L) "Budget created" else "Budget saved", close = true) { container.repository.saveBudget(b) }
    fun deleteBudget(id: Long) = act("Budget deleted", close = true) { container.repository.deleteBudget(id) }

    fun saveBill(b: BillEntity) = act(if (b.id == 0L) "Bill added" else "Bill saved", close = true) { container.repository.saveBill(b) }
    fun deleteBill(id: Long) = act("Bill deleted", close = true) { container.repository.deleteBill(id) }
    fun pay(bill: BillEntity, due: LocalDate, accountId: Long, amount: Long, date: LocalDate) = act("Payment recorded") {
        val at = LocalDateTime.of(date, if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        container.repository.payBill(bill, due, accountId, amount, at)
    }
    fun skip(bill: BillEntity, due: LocalDate) = act("Occurrence skipped") { container.repository.skipOccurrence(bill, due) }
    fun undoPayment(id: Long) = act("Payment undone") { container.repository.undoPayment(id) }
    fun setActive(bill: BillEntity, active: Boolean) = act(if (active) "Bill resumed" else "Bill paused") { container.repository.saveBill(bill.copy(active = active)) }

    fun saveGoal(g: GoalEntity) = act(if (g.id == 0L) "Goal created" else "Goal saved", close = true) { container.repository.saveGoal(g) }
    fun deleteGoal(id: Long) = act("Goal deleted", close = true) { container.repository.deleteGoal(id) }
    fun archiveGoal(g: GoalEntity) = act(if (g.archived) "Goal restored" else "Goal archived", close = true) { container.repository.saveGoal(g.copy(archived = !g.archived)) }
    fun goalEntry(goalId: Long, amount: Long, note: String) = act(if (amount > 0) "Contribution added" else "Withdrawal recorded") {
        container.repository.addGoalEntry(goalId, amount, LocalDate.now(), note)
    }
    fun deleteGoalEntry(id: Long) = act { container.repository.deleteGoalEntry(id) }
}

/* ================================================================== */
/* Budget editor                                                       */
/* ================================================================== */

@Composable
fun BudgetEditScreen(budgetId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "budget-$budgetId") { PlanEditViewModel(it) }
    VmEffects(vm, onDone = onClose)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val existing = l.data.budgets.firstOrNull { it.id == budgetId }
    val c = Eq.colors
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var period by rememberSaveable { mutableStateOf(existing?.period ?: BudgetPeriod.MONTHLY) }
    var limit by rememberSaveable { mutableStateOf(existing?.let { Money.editable(it.limitMinor, it.currency) } ?: "") }
    var sheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currency = existing?.currency ?: l.base
    val duplicate = l.data.budgets.any { it.id != budgetId && it.categoryId == categoryId && it.period == period }
    val cat = categoryId?.let { l.categoriesById[it] }
    val suggestion = remember(l, categoryId) {
        val months = (1..3).map { back ->
            val ref = l.today.minusMonths(back.toLong())
            val r = com.equinoxtrack.app.domain.Periods.budgetMonth(ref, l.settings.periodStartDay)
            if (categoryId == null) l.totals(r).expense else l.categorySpent(r, categoryId!!)
        }
        months.sum() / 3
    }

    Column(Modifier.fillMaxSize()) {
        EqTopBar(if (existing == null) "New budget" else "Edit budget", onBack = onClose) {
            if (existing != null) CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }, tint = c.expense)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Overline("Applies to")
            EqCard(onClick = { sheet = true }, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.of(cat?.icon ?: "trending"), cat?.let { Color(it.color) } ?: c.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat?.name ?: "All spending", style = Eq.type.titleSmall, color = c.textPrimary)
                        Text(if (cat == null) "Overall budget across every expense" else "Expenses in this category", style = Eq.type.bodySmall, color = c.textSecondary)
                    }
                    Text("Change", style = Eq.type.labelLarge, color = c.accent)
                }
            }
            Overline("Period")
            SegmentedControl(BudgetPeriod.entries.toList(), period, { period = it }, label = { it.label })
            EqTextField(limit, { limit = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Spending limit", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol)
            if (suggestion > 0) {
                ChoiceChip("Avg last 3 months: ${Money.format(suggestion, l.base)}", false, {
                    val monthly = if (period == BudgetPeriod.YEARLY) suggestion * 12 else suggestion
                    limit = Money.editable(monthly, currency)
                })
            }
            if (duplicate) Tag("A ${period.label.lowercase()} budget for this already exists", c.expense)
        }
        Box(Modifier.padding(18.dp).navigationBarsPadding()) {
            PrimaryButton("Save budget", {
                val minor = Money.parseToMinor(limit, currency) ?: 0L
                vm.saveBudget((existing ?: BudgetEntity(categoryId = null, limitMinor = 0, currency = currency, period = period)).copy(categoryId = categoryId, period = period, limitMinor = minor))
            }, Modifier.fillMaxWidth(), enabled = !duplicate)
        }
    }
    if (sheet) {
        val items = listOf(PickerItem<Long?>(null, "All spending", "Overall budget", AppIcons.of("trending"), c.accent)) +
            l.data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { PickerItem<Long?>(it.id, it.name, null, AppIcons.of(it.icon), Color(it.color)) }
        PickerSheet("Budget for", items, categoryId, { categoryId = it }, { sheet = false }, searchable = true)
    }
    if (confirmDelete && existing != null) ConfirmDialog("Delete budget?", "Your spending history is not affected.", "Delete", { vm.deleteBudget(existing.id) }, { confirmDelete = false })
}

/* ================================================================== */
/* Bill editor                                                         */
/* ================================================================== */

@Composable
fun BillEditScreen(billId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "billEdit-$billId") { PlanEditViewModel(it) }
    VmEffects(vm, onDone = onClose)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val existing = l.data.bills.firstOrNull { it.id == billId }
    val c = Eq.colors
    val defaultAccount = l.activeAccounts.firstOrNull()

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: defaultAccount?.id) }
    val account = accountId?.let { l.accountsById[it] }
    val currency = existing?.currency ?: account?.currency ?: l.base
    var amount by rememberSaveable { mutableStateOf(existing?.let { Money.editable(it.amountMinor, it.currency) } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: l.data.categories.firstOrNull { it.name == "Utilities" }?.id) }
    var frequency by rememberSaveable { mutableStateOf(existing?.frequency ?: BillFrequency.MONTHLY) }
    var interval by rememberSaveable { mutableIntStateOf(existing?.interval ?: 1) }
    var anchor by rememberSaveable { mutableStateOf(existing?.anchorEpochDay ?: l.today.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(existing?.endEpochDay) }
    var subscription by rememberSaveable { mutableStateOf(existing?.isSubscription ?: false) }
    var reminder by rememberSaveable { mutableIntStateOf(existing?.reminderDaysBefore ?: 2) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: 0xFFFFC145) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: "receipt") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var pickAnchor by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val paymentsExist = existing != null && l.data.billPayments.any { it.billId == existing.id }

    Column(Modifier.fillMaxSize()) {
        EqTopBar(if (existing == null) "New bill" else "Edit bill", onBack = onClose) {
            if (existing != null) CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }, tint = c.expense)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EqTextField(name, { name = it.take(40) }, "Name", placeholder = "Rent, Meralco, Netflix…")
            EqTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(account?.name ?: "Pay from…", account != null, { sheet = "account" }, icon = AppIcons.of(account?.icon ?: "wallet"))
                val cat = categoryId?.let { l.categoriesById[it] }
                ChoiceChip(cat?.name ?: "Category", cat != null, { sheet = "category" }, icon = AppIcons.of(cat?.icon ?: "tag"))
            }
            Overline("Repeats")
            SegmentedControl(BillFrequency.entries.toList(), frequency, { frequency = it }, label = { it.label })
            if (frequency != BillFrequency.ONCE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", style = Eq.type.bodyLarge, color = c.textSecondary)
                    Spacer(Modifier.width(10.dp))
                    CircleIconButton(Icons.Rounded.Remove, "Less", { if (interval > 1) interval-- }, size = 34.dp)
                    Text(interval.toString(), style = Eq.type.titleLarge, color = c.textPrimary, modifier = Modifier.padding(horizontal = 14.dp))
                    CircleIconButton(Icons.Rounded.Add, "More", { if (interval < 52) interval++ }, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (frequency) { BillFrequency.WEEKLY -> "week"; BillFrequency.MONTHLY -> "month"; else -> "year" } + if (interval > 1) "s" else "",
                        style = Eq.type.bodyLarge, color = c.textSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip((if (existing == null) "First due " else "Starts ") + Fmt.full(LocalDate.ofEpochDay(anchor)), false, { pickAnchor = true }, icon = Icons.Rounded.DateRange)
                if (frequency != BillFrequency.ONCE) ChoiceChip(end?.let { "Ends " + Fmt.short(LocalDate.ofEpochDay(it)) } ?: "No end date", end != null, { if (end != null) end = null else pickEnd = true })
            }
            if (paymentsExist) Text("Changing the start date or schedule keeps existing payments; occurrences are matched by due date.", style = Eq.type.bodySmall, color = c.textTertiary)
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SettingRow(Icons.Rounded.Subscriptions, "Subscription", "Track in subscription totals", trailing = { EqSwitch(subscription) { subscription = it } })
                SettingRow(Icons.Rounded.Notifications, "Remind me", if (reminder == 0) "On the due date" else "$reminder day${if (reminder > 1) "s" else ""} before", onClick = { sheet = "reminder" })
            }
            Overline("Look")
            SwatchPicker(Defaults.swatches, color, { color = it })
            IconPicker(listOf("receipt", "home", "bolt", "wifi", "mobile", "subscriptions", "shield", "car", "school", "health", "fitness", "music", "movie", "game", "cash"), icon, Color(color), { icon = it })
            EqTextField(note, { note = it.take(120) }, "Note (optional)")
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(18.dp).navigationBarsPadding()) {
            PrimaryButton("Save bill", {
                val minor = Money.parseToMinor(amount, currency) ?: 0L
                val base = existing ?: BillEntity(name = "", amountMinor = 0, currency = currency, accountId = null, categoryId = null, frequency = frequency, anchorEpochDay = anchor, color = color, icon = icon)
                vm.saveBill(
                    base.copy(
                        name = name, amountMinor = minor, currency = currency, accountId = accountId, categoryId = categoryId, frequency = frequency,
                        interval = interval, anchorEpochDay = anchor, endEpochDay = end?.takeIf { frequency != BillFrequency.ONCE }, isSubscription = subscription,
                        reminderDaysBefore = reminder, color = color, icon = icon, note = note,
                    )
                )
            }, Modifier.fillMaxWidth())
        }
    }
    when (sheet) {
        "account" -> PickerSheet("Pay from", l.activeAccounts.map { PickerItem<Long?>(it.id, it.name, it.currency, AppIcons.of(it.icon), Color(it.color)) }, accountId, { accountId = it }, { sheet = null })
        "category" -> PickerSheet("Category", l.data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { PickerItem<Long?>(it.id, it.name, null, AppIcons.of(it.icon), Color(it.color)) }, categoryId, { categoryId = it }, { sheet = null }, searchable = true)
        "reminder" -> PickerSheet("Remind me", listOf(0, 1, 2, 3, 5, 7).map { PickerItem(it, if (it == 0) "On the due date" else "$it day${if (it > 1) "s" else ""} before") }, reminder, { reminder = it }, { sheet = null })
    }
    if (pickAnchor) EqDatePickerDialog(LocalDate.ofEpochDay(anchor), { pickAnchor = false }) { anchor = it.toEpochDay() }
    if (pickEnd) EqDatePickerDialog(LocalDate.ofEpochDay(anchor).plusYears(1), { pickEnd = false }) { end = it.toEpochDay() }
    if (confirmDelete && existing != null) ConfirmDialog(
        "Delete ${existing.name}?",
        "The bill and its payment records are removed. Expenses already recorded for past payments stay in your history.",
        "Delete", { vm.deleteBill(existing.id) }, { confirmDelete = false },
    )
}

/* ================================================================== */
/* Bill detail                                                         */
/* ================================================================== */

@Composable
fun BillDetailScreen(billId: Long, onBack: () -> Unit, onEdit: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val vm = appViewModel(key = "bill-$billId") { PlanEditViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val bill = l.data.bills.firstOrNull { it.id == billId }
    if (bill == null) { LaunchedEffect(Unit) { onBack() }; return }
    val c = Eq.colors
    val hidden = l.settings.hideBalances
    val payments = l.data.billPayments.filter { it.billId == billId }
    val next = BillSchedule.nextUnpaid(bill, l.data.billPayments, l.today)
    val overdueCount = BillSchedule.unpaidOccurrences(bill, l.data.billPayments, l.today.minusDays(1)).size
    val tint = Color(bill.color)
    var paySheet by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<Long?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            EqTopBar(bill.name, onBack = onBack, subtitle = BillSchedule.describe(bill)) {
                CircleIconButton(Icons.Rounded.Edit, "Edit", onEdit)
            }
        }
        item {
            HeroSurface(Modifier.fillMaxWidth(), brush = Brush.linearGradient(listOf(lerp(tint, Color(0xFF060914), 0.4f), Color(0xFF0B1024))), glow = tint) {
                Column(Modifier.padding(22.dp)) {
                    Overline(if (next == null) "No upcoming payments" else Fmt.dueLabel(java.time.temporal.ChronoUnit.DAYS.between(l.today, next)), color = Color.White.copy(alpha = 0.7f))
                    MoneyText(bill.amountMinor, bill.currency, style = Eq.type.displaySmall, color = Color.White, hidden = hidden)
                    if (next != null) Text("Next due ${Fmt.full(next)}", style = Eq.type.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (overdueCount > 0) Tag("$overdueCount overdue", c.expense)
                        if (bill.isSubscription) Tag("≈ ${Money.format(BillSchedule.yearlyEquivalent(bill), bill.currency)}/yr", Color.White)
                        if (!bill.active) Tag("Paused", c.warning)
                    }
                }
            }
        }
        if (next != null && bill.active) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Pay ${Fmt.short(next)}", { paySheet = true }, Modifier.weight(1.4f), icon = Icons.Rounded.CheckCircle)
                SecondaryButton("Skip", { vm.skip(bill, next) }, Modifier.weight(1f), icon = Icons.Rounded.SkipNext)
            }
        }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SettingRow(Icons.Rounded.Pause, if (bill.active) "Pause this bill" else "Resume this bill", "Paused bills aren't reserved or reminded", trailing = { EqSwitch(bill.active) { vm.setActive(bill, it) } })
                val account = bill.accountId?.let { l.accountsById[it] }
                SettingRow(AppIcons.of(account?.icon ?: "wallet"), "Pays from ${account?.name ?: "—"}", "Category: ${l.categoryName(bill.categoryId)}")
            }
        }
        item { SectionHeader("Payment history · ${payments.count { !it.skipped }} paid") }
        if (payments.isEmpty()) item { EqCard { Text("No payments recorded yet.", style = Eq.type.bodyMedium, color = c.textSecondary) } }
        else item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                payments.forEachIndexed { i, p ->
                    val tx = p.transactionId?.let { id -> l.data.transactions.firstOrNull { it.tx.id == id } }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Due ${Fmt.full(LocalDate.ofEpochDay(p.dueEpochDay))}", style = Eq.type.titleSmall, color = c.textPrimary)
                            Text(
                                if (p.skipped) "Skipped" else "Paid ${tx?.let { Fmt.short(l.dateOf(it)) } ?: ""} · ${tx?.let { l.accountsById[it.tx.accountId]?.name } ?: ""}",
                                style = Eq.type.bodySmall, color = if (p.skipped) c.textTertiary else c.income,
                            )
                        }
                        if (!p.skipped) MoneyText(tx?.tx?.amountMinor ?: p.amountMinor, tx?.let { l.currencyOf(it.tx.accountId) } ?: bill.currency, hidden = hidden)
                        if (tx != null) CircleIconButton(Icons.Rounded.Edit, "Open expense", { onOpenTransaction(tx.tx.id) }, size = 32.dp, modifier = Modifier.padding(start = 8.dp))
                        CircleIconButton(Icons.Rounded.Undo, "Undo", { undo = p.id }, size = 32.dp, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (i < payments.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.stroke))
                }
            }
        }
    }

    if (paySheet && next != null) PaySheet(l, bill, next, onDismiss = { paySheet = false }) { accountId, amount, date ->
        vm.pay(bill, next, accountId, amount, date)
        paySheet = false
    }
    undo?.let { id ->
        ConfirmDialog(
            "Undo this payment?", "The occurrence becomes unpaid again. If an expense was recorded for it, that expense is deleted and the money returns to the account.",
            "Undo", { vm.undoPayment(id) }, { undo = null },
        )
    }
}

@Composable
private fun PaySheet(l: com.equinoxtrack.app.domain.Ledger, bill: BillEntity, due: LocalDate, onDismiss: () -> Unit, onPay: (Long, Long, LocalDate) -> Unit) {
    val c = Eq.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var accountId by remember { mutableStateOf(bill.accountId?.takeIf { id -> l.accountsById[id]?.archived == false } ?: l.activeAccounts.firstOrNull()?.id) }
    val account = accountId?.let { l.accountsById[it] }
    val cur = account?.currency ?: bill.currency
    var amount by remember { mutableStateOf(Money.editable(if (cur == bill.currency) bill.amountMinor else (l.converter.convert(bill.amountMinor, bill.currency, cur) ?: bill.amountMinor), cur)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pay ${bill.name}", style = Eq.type.headlineSmall, color = c.textPrimary)
            Text("For the occurrence due ${Fmt.full(due)}. One expense is recorded — it won't be counted again.", style = Eq.type.bodySmall, color = c.textSecondary)
            EqTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount paid", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(cur).symbol)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(account?.name ?: "Choose account", account != null, { pickAccount = true }, icon = AppIcons.of(account?.icon ?: "wallet"))
                ChoiceChip(Fmt.full(date), false, { pickDate = true }, icon = Icons.Rounded.DateRange)
            }
            PrimaryButton("Record payment", {
                val id = accountId
                val minor = Money.parseToMinor(amount, cur)
                if (id != null && minor != null) onPay(id, minor, date)
            }, Modifier.fillMaxWidth(), enabled = accountId != null)
            Spacer(Modifier.height(12.dp))
        }
    }
    if (pickDate) EqDatePickerDialog(date, { pickDate = false }) { date = it }
    if (pickAccount) PickerSheet("Pay from", l.activeAccounts.map { PickerItem(it.id, it.name, Money.format(l.balance(it.id), it.currency), AppIcons.of(it.icon), Color(it.color)) }, accountId, { accountId = it }, { pickAccount = false })
}

/* ================================================================== */
/* Goal editor & detail                                                */
/* ================================================================== */

@Composable
fun GoalEditScreen(goalId: Long, onClose: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel(key = "goalEdit-$goalId") { PlanEditViewModel(it) }
    var deleted by remember { mutableStateOf(false) }
    VmEffects(vm, onDone = { if (deleted) onDeleted() else onClose() })
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val existing = l.data.goals.firstOrNull { it.id == goalId }
    val c = Eq.colors
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    val currency = existing?.currency ?: l.base
    var target by rememberSaveable { mutableStateOf(existing?.let { Money.editable(it.targetMinor, it.currency) } ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.targetEpochDay) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: 0xFF14D3B4) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: "savings") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        EqTopBar(if (existing == null) "New goal" else "Edit goal", onBack = onClose) {
            if (existing != null) CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }, tint = c.expense)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.of(icon), Color(color), size = 48.dp, corner = 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name.ifBlank { "Goal name" }, style = Eq.type.titleMedium, color = if (name.isBlank()) c.textTertiary else c.textPrimary)
                        Text(date?.let { "by ${Fmt.full(LocalDate.ofEpochDay(it))}" } ?: "No deadline", style = Eq.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
            EqTextField(name, { name = it.take(40) }, "Name", placeholder = "Emergency fund, Japan trip…")
            EqTextField(target, { target = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Target amount", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(date?.let { "Target " + Fmt.full(LocalDate.ofEpochDay(it)) } ?: "Add target date", date != null, { pickDate = true }, icon = Icons.Rounded.DateRange)
                if (date != null) ChoiceChip("Clear", false, { date = null })
            }
            Overline("Look")
            SwatchPicker(Defaults.swatches, color, { color = it })
            IconPicker(AppIcons.goalChoices, icon, Color(color), { icon = it })
            EqTextField(note, { note = it.take(120) }, "Note (optional)")
            if (existing != null) SecondaryButton(if (existing.archived) "Restore goal" else "Archive goal", { vm.archiveGoal(existing) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(18.dp).navigationBarsPadding()) {
            PrimaryButton("Save goal", {
                val base = existing ?: GoalEntity(name = "", targetMinor = 0, currency = currency, color = color, icon = icon)
                vm.saveGoal(base.copy(name = name, targetMinor = Money.parseToMinor(target, currency) ?: 0L, targetEpochDay = date, color = color, icon = icon, note = note))
            }, Modifier.fillMaxWidth())
        }
    }
    if (pickDate) EqDatePickerDialog(date?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusMonths(6), { pickDate = false }) { date = it.toEpochDay() }
    if (confirmDelete && existing != null) ConfirmDialog("Delete ${existing.name}?", "Its contribution history is removed. Account balances are unaffected — goals are earmarks.", "Delete", { deleted = true; vm.deleteGoal(existing.id) }, { confirmDelete = false })
}

@Composable
fun GoalDetailScreen(goalId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm = appViewModel(key = "goal-$goalId") { PlanEditViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val goal = l.data.goals.firstOrNull { it.id == goalId }
    if (goal == null) { LaunchedEffect(Unit) { onBack() }; return }
    val c = Eq.colors
    val p = GoalCalculator.progress(goal, l.data.goalEntries, l.today)
    val entries = l.data.goalEntries.filter { it.goalId == goalId }
    val tint = Color(goal.color)
    var dialog by remember { mutableStateOf<Boolean?>(null) } // true = contribute, false = withdraw

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { EqTopBar(goal.name, onBack = onBack, subtitle = goal.targetEpochDay?.let { "Target ${Fmt.full(LocalDate.ofEpochDay(it))}" } ?: "No deadline") { CircleIconButton(Icons.Rounded.Edit, "Edit", onEdit) } }
        item {
            HeroSurface(Modifier.fillMaxWidth(), brush = Brush.linearGradient(listOf(lerp(tint, Color(0xFF060914), 0.45f), Color(0xFF0B1024))), glow = tint) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RingProgress(p.fraction, Modifier.size(170.dp), color = tint, thickness = 14.dp, track = Color.White.copy(alpha = 0.1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${p.percent}%", style = Eq.type.displaySmall, color = Color.White)
                            Text(if (p.completed) "complete" else "saved", style = Eq.type.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        GoalStat("Saved", Money.format(p.saved, goal.currency), Modifier.weight(1f))
                        GoalStat("Target", Money.format(goal.targetMinor, goal.currency), Modifier.weight(1f))
                        GoalStat("To go", Money.format(p.remaining, goal.currency), Modifier.weight(1f))
                    }
                    if (p.monthlyNeeded != null && !p.completed) {
                        Spacer(Modifier.height(12.dp))
                        Tag("Save ${Money.format(p.monthlyNeeded, goal.currency)} a month to make it", Color.White)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Add money", { dialog = true }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                SecondaryButton("Withdraw", { dialog = false }, Modifier.weight(1f), icon = Icons.Rounded.Remove)
            }
        }
        item {
            Text(
                "Goal contributions earmark money that already sits in your accounts. They are never counted as income and do not move money between accounts.",
                style = Eq.type.bodySmall, color = c.textTertiary,
            )
        }
        item { SectionHeader("History") }
        if (entries.isEmpty()) item { EqCard { Text("No contributions yet.", style = Eq.type.bodyMedium, color = c.textSecondary) } }
        else item {
            EqCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                entries.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (e.amountMinor > 0) "Contribution" else "Withdrawal", style = Eq.type.titleSmall, color = c.textPrimary)
                            Text(Fmt.full(LocalDate.ofEpochDay(e.epochDay)) + if (e.note.isNotBlank()) " · ${e.note}" else "", style = Eq.type.bodySmall, color = c.textSecondary)
                        }
                        MoneyText(e.amountMinor, goal.currency, color = if (e.amountMinor > 0) c.income else c.expense, signed = true)
                        CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { vm.deleteGoalEntry(e.id) }, size = 32.dp, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (i < entries.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.stroke))
                }
            }
        }
    }

    dialog?.let { contribute ->
        var text by remember(contribute) { mutableStateOf("") }
        var note by remember(contribute) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = c.surfaceRaised,
            title = { Text(if (contribute) "Add to ${goal.name}" else "Withdraw from ${goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EqTextField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(goal.currency).symbol)
                    EqTextField(note, { note = it.take(80) }, "Note (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Money.parseToMinor(text, goal.currency)
                    if (v != null && v > 0) vm.goalEntry(goal.id, if (contribute) v else -v, note)
                    dialog = null
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

@Composable
private fun GoalStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = Eq.type.titleSmall, color = Color.White, maxLines = 1)
    }
}
