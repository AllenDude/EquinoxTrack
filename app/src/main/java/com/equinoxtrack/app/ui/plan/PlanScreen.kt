package com.equinoxtrack.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.domain.BillSchedule
import com.equinoxtrack.app.domain.BudgetPeriod
import com.equinoxtrack.app.domain.BudgetStatus
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.SafeToSpendLimit
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.BarGroup
import com.equinoxtrack.app.ui.components.ChartLegend
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.EmptyState
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.EqSwitch
import com.equinoxtrack.app.ui.components.EqTextField
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.GroupedBarChart
import com.equinoxtrack.app.ui.components.HeroSurface
import com.equinoxtrack.app.ui.components.HorizonGauge
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.RingProgress
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.SettingRow
import com.equinoxtrack.app.ui.components.Tag
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.home.BillDueRow
import com.equinoxtrack.app.ui.home.BudgetLine
import com.equinoxtrack.app.ui.home.BudgetUi
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class PlanViewModel(c: AppContainer) : BaseViewModel(c) {
    fun setProtectGoals(v: Boolean) { viewModelScope.launch { container.settings.setProtectGoals(v) } }
    fun setBuffer(minor: Long) { viewModelScope.launch { container.settings.setBuffer(minor) } }
    fun setStartDay(day: Int) { viewModelScope.launch { container.settings.setPeriodStartDay(day) } }
}

private fun budgetUi(l: Ledger, s: BudgetStatus): BudgetUi {
    val cat = s.budget.categoryId?.let { l.categoriesById[it] }
    val elapsed = when {
        l.today.isBefore(s.period.start) -> 0f
        l.today.isAfter(s.period.endInclusive) -> 1f
        else -> (ChronoUnit.DAYS.between(s.period.start, l.today) + 1).toFloat() / s.period.lengthDays
    }
    return BudgetUi(s.budget.id, cat?.name ?: "All spending", cat?.icon ?: "trending", cat?.color ?: 0xFF7C5CFF, s.spent, s.limit, s.fraction, s.over, elapsed)
}

@Composable
fun PlanScreen(
    initialTab: Int,
    bottomPadding: PaddingValues,
    onEditBudget: (Long) -> Unit,
    onOpenBill: (Long) -> Unit,
    onEditBill: (Long) -> Unit,
    onOpenGoal: (Long) -> Unit,
    onEditGoal: (Long) -> Unit,
    onOpenSafe: () -> Unit,
) {
    val vm = appViewModel { PlanViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { EqTopBar("Plan", subtitle = "Budgets, bills and savings goals") }
        item {
            SegmentedControl(listOf(0, 1, 2), tab, { tab = it }, label = { listOf("Budgets", "Bills", "Goals")[it] })
        }
        when (tab) {
            0 -> budgetsTab(l, onEditBudget, onOpenSafe)
            1 -> billsTab(l, onOpenBill, onEditBill)
            else -> goalsTab(l, onOpenGoal, onEditGoal)
        }
    }
}

/* ---------------- Budgets ---------------- */

private fun LazyListScope.budgetsTab(l: Ledger, onEdit: (Long) -> Unit, onOpenSafe: () -> Unit) {
    item { BudgetsContent(l, onEdit, onOpenSafe) }
}

@Composable
private fun BudgetsContent(l: Ledger, onEdit: (Long) -> Unit, onOpenSafe: () -> Unit) {
    val c = Eq.colors
    var yearly by rememberSaveable { mutableStateOf(false) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val hidden = l.settings.hideBalances
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SegmentedControl(listOf(false, true), yearly, { yearly = it }, label = { if (it) "Yearly summary" else "Monthly" })
        if (!yearly) {
            val ym = YearMonth.from(l.today).plusMonths(monthOffset.toLong())
            val reference = Periods.budgetMonthOf(ym, l.settings.periodStartDay).start
            val statuses = l.data.budgets.filter { it.period == BudgetPeriod.MONTHLY }.map { l.budgetStatus(it, reference) }
            val range = Periods.budgetMonthOf(ym, l.settings.periodStartDay)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.ChevronLeft, "Previous", { monthOffset-- }, size = 36.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.month(ym.atDay(1)), style = Eq.type.titleMedium, color = c.textPrimary)
                    Text("${Fmt.short(range.start)} – ${Fmt.short(range.endInclusive)}", style = Eq.type.bodySmall, color = c.textSecondary)
                }
                CircleIconButton(Icons.Rounded.ChevronRight, "Next", { monthOffset++ }, size = 36.dp)
            }
            val overall = statuses.firstOrNull { it.budget.categoryId == null }
            if (overall != null) {
                EqCard(onClick = { onEdit(overall.budget.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(overall.fraction, Modifier.size(96.dp), color = if (overall.over) c.expense else c.accent, thickness = 10.dp) {
                            Text(Fmt.percent(overall.fraction.coerceAtMost(9.99f)), style = Eq.type.titleMedium, color = c.textPrimary)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Overline("Overall budget")
                            MoneyText(overall.remaining, l.base, style = Eq.type.headlineMedium, color = if (overall.over) c.expense else c.textPrimary, hidden = hidden)
                            Text(if (overall.over) "over the limit" else "left to spend", style = Eq.type.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            Text("${Money.format(overall.spent, l.base)} of ${Money.format(overall.limit, l.base)}", style = Eq.type.labelMedium, color = c.textTertiary)
                        }
                    }
                    if (monthOffset == 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.pressable(onOpenSafe, RoundedCornerShape(12.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Safe to spend today: ", style = Eq.type.bodySmall, color = c.textSecondary)
                            MoneyText(l.safeToSpend.leftToday.coerceAtLeast(0), l.base, style = Eq.type.labelLarge, color = c.accent, hidden = hidden)
                        }
                    }
                }
            } else {
                EqCard(onClick = { onEdit(0) }) {
                    Text("Add an overall monthly budget", style = Eq.type.titleSmall, color = c.textPrimary)
                    Text("Caps Safe-to-Spend and tracks your pace across every category.", style = Eq.type.bodySmall, color = c.textSecondary)
                }
            }
            val categoryBudgets = statuses.filter { it.budget.categoryId != null }.sortedByDescending { it.fraction }
            SectionHeader("Category budgets", action = "Add", onAction = { onEdit(0) })
            if (categoryBudgets.isEmpty()) {
                EqCard { EmptyState(Icons.Rounded.Add, "No category budgets", "Set limits for the categories you want to keep an eye on.", action = "Add budget", onAction = { onEdit(0) }) }
            } else EqCard {
                categoryBudgets.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    Box(Modifier.pressable({ onEdit(s.budget.id) }, RoundedCornerShape(12.dp))) {
                        Column {
                            BudgetLine(budgetUi(l, s), l.base, hidden)
                            Text(
                                if (s.over) "Over by ${Money.format(-s.remaining, l.base)}" else "${Money.format(s.remaining, l.base)} left" + if (s.aheadOfPace) " · spending faster than usual" else "",
                                style = Eq.type.labelMedium, color = if (s.over) c.expense else if (s.aheadOfPace) c.warning else c.textTertiary,
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                            )
                        }
                    }
                }
            }
        } else {
            var year by rememberSaveable { mutableIntStateOf(l.today.year) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.ChevronLeft, "Previous year", { year-- }, size = 36.dp)
                Text(year.toString(), style = Eq.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                CircleIconButton(Icons.Rounded.ChevronRight, "Next year", { year++ }, size = 36.dp)
            }
            val overall = l.overallMonthlyBudget
            val months = (1..12).map { m ->
                val ym = YearMonth.of(year, m)
                val spent = l.totals(Periods.budgetMonthOf(ym, l.settings.periodStartDay)).expense
                BarGroup(ym.month.name.take(1), overall?.let { l.toBase(it.limitMinor, it.currency) } ?: 0L, spent)
            }
            val yearTotals = l.totals(Periods.year(year))
            var sel by remember { mutableStateOf<Int?>(null) }
            EqCard {
                Overline("Spending vs monthly budget")
                Spacer(Modifier.height(4.dp))
                val s = sel
                if (s != null) {
                    Text("${YearMonth.of(year, s + 1).month.name.lowercase().replaceFirstChar { it.uppercase() }}: ${Money.format(months[s].b, l.base)} spent" + if (overall != null) " of ${Money.format(months[s].a, l.base)}" else "", style = Eq.type.labelLarge, color = c.textPrimary)
                } else ChartLegend(listOf(c.accent.copy(alpha = 0.5f) to "Budget", c.expense to "Spent"))
                Spacer(Modifier.height(10.dp))
                GroupedBarChart(months, Modifier.fillMaxWidth().height(170.dp), colorA = c.accent.copy(alpha = 0.45f), colorB = c.expense, selected = sel, onSelect = { sel = it })
                Spacer(Modifier.height(14.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Spent in $year", style = Eq.type.labelMedium, color = c.textTertiary)
                        MoneyText(yearTotals.expense, l.base, style = Eq.type.titleMedium, hidden = hidden)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Months over budget", style = Eq.type.labelMedium, color = c.textTertiary)
                        Text(if (overall == null) "—" else months.count { it.a > 0 && it.b > it.a }.toString(), style = Eq.type.titleMedium, color = c.textPrimary)
                    }
                }
            }
            val yearly = l.data.budgets.filter { it.period == BudgetPeriod.YEARLY }.map { l.budgetStatus(it, YearMonth.of(year, 6).atDay(15)) }
            SectionHeader("Yearly budgets", action = "Add", onAction = { onEdit(0) })
            if (yearly.isEmpty()) {
                EqCard { Text("Yearly budgets suit irregular costs like travel, gifts or insurance.", style = Eq.type.bodySmall, color = c.textSecondary) }
            } else EqCard {
                yearly.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Box(Modifier.pressable({ onEdit(s.budget.id) }, RoundedCornerShape(12.dp))) { BudgetLine(budgetUi(l, s), l.base, hidden) }
                }
            }
        }
    }
}

/* ---------------- Bills ---------------- */

private fun LazyListScope.billsTab(l: Ledger, onOpen: (Long) -> Unit, onEdit: (Long) -> Unit) {
    val dues = l.billDues(l.today.plusDays(30))
    val overdue = dues.filter { it.overdue }
    val upcoming = dues.filter { !it.overdue }
    val subs = l.data.bills.filter { it.isSubscription && it.active }
    item {
        BillSummary(l, dues.sumOf { l.toBase(it.amountMinor, it.bill.currency) }, overdue.size, subs.sumOf { l.toBase(BillSchedule.monthlyEquivalent(it), it.currency) }, onEdit)
    }
    if (overdue.isNotEmpty()) {
        item { SectionHeader("Overdue") }
        item { EqCard(padding = PaddingValues(8.dp)) { overdue.forEach { BillDueRow(it, l.settings.hideBalances) { onOpen(it.bill.id) } } } }
    }
    item { SectionHeader("Next 30 days") }
    item {
        if (upcoming.isEmpty()) EqCard { Text("Nothing due in the next 30 days.", style = Eq.type.bodyMedium, color = Eq.colors.textSecondary) }
        else EqCard(padding = PaddingValues(8.dp)) { upcoming.forEach { BillDueRow(it, l.settings.hideBalances) { onOpen(it.bill.id) } } }
    }
    item { SectionHeader("All bills & subscriptions", action = "Add", onAction = { onEdit(0) }) }
    item { AllBills(l, onOpen, onEdit) }
}

@Composable
private fun BillSummary(l: Ledger, dueTotal: Long, overdueCount: Int, subsMonthly: Long, onAdd: (Long) -> Unit) {
    val c = Eq.colors
    val hidden = l.settings.hideBalances
    HeroSurface(Modifier.fillMaxWidth(), glow = c.warning) {
        Column(Modifier.padding(20.dp)) {
            Overline("Due in the next 30 days", color = Color.White.copy(alpha = 0.7f))
            MoneyText(dueTotal, l.base, style = Eq.type.displaySmall, color = Color.White, hidden = hidden, animate = true)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (overdueCount > 0) Tag("$overdueCount overdue", c.expense) else Tag("All caught up", c.income)
                Spacer(Modifier.width(8.dp))
                Text("Subscriptions ≈ ", style = Eq.type.bodySmall, color = Color.White.copy(alpha = 0.65f))
                MoneyText(subsMonthly, l.base, style = Eq.type.labelLarge, color = Color.White, hidden = hidden)
                Text(" / mo", style = Eq.type.bodySmall, color = Color.White.copy(alpha = 0.65f))
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.Add, "Add bill", { onAdd(0) }, background = Color.White.copy(alpha = 0.12f), tint = Color.White, size = 38.dp)
            }
        }
    }
}

@Composable
private fun AllBills(l: Ledger, onOpen: (Long) -> Unit, onEdit: (Long) -> Unit) {
    val c = Eq.colors
    if (l.data.bills.isEmpty()) {
        EqCard { EmptyState(Icons.Rounded.Event, "No bills yet", "Add rent, utilities, phone plans and subscriptions. EquinoxTrack reserves them in Safe-to-Spend and reminds you before they're due.", action = "Add bill", onAction = { onEdit(0) }) }
        return
    }
    EqCard(padding = PaddingValues(8.dp)) {
        l.data.bills.sortedWith(compareBy({ !it.active }, { it.name.lowercase() })).forEach { bill ->
            val next = BillSchedule.nextUnpaid(bill, l.data.billPayments, l.today)
            Row(
                Modifier.fillMaxWidth().pressable({ onOpen(bill.id) }, RoundedCornerShape(16.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(if (bill.isSubscription) Icons.Rounded.Subscriptions else AppIcons.of(bill.icon), Color(bill.color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bill.name, style = Eq.type.titleSmall, color = if (bill.active) c.textPrimary else c.textTertiary)
                    Text(
                        BillSchedule.describe(bill) + " · " + when {
                            !bill.active -> "Paused"
                            next == null -> "Completed"
                            else -> "next ${Fmt.short(next)}"
                        },
                        style = Eq.type.bodySmall, color = c.textSecondary,
                    )
                }
                MoneyText(bill.amountMinor, bill.currency, hidden = l.settings.hideBalances)
            }
        }
    }
}

/* ---------------- Goals ---------------- */

private fun LazyListScope.goalsTab(l: Ledger, onOpen: (Long) -> Unit, onEdit: (Long) -> Unit) {
    val progress = l.goalProgress()
    item {
        val c = Eq.colors
        HeroSurface(Modifier.fillMaxWidth(), glow = c.income) {
            Column(Modifier.padding(20.dp)) {
                Overline("Saved toward goals", color = Color.White.copy(alpha = 0.7f))
                MoneyText(l.goalReserve, l.base, style = Eq.type.displaySmall, color = Color.White, hidden = l.settings.hideBalances, animate = true)
                Text(
                    if (l.settings.protectGoals) "Earmarked inside your accounts and protected from Safe-to-Spend." else "Earmarked inside your accounts (not reserved from Safe-to-Spend).",
                    style = Eq.type.bodySmall, color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
    item { SectionHeader("Goals", action = "New goal", onAction = { onEdit(0) }) }
    if (progress.isEmpty()) item {
        EqCard { EmptyState(Icons.Rounded.Savings, "Start a savings goal", "A trip, an emergency fund, a new laptop… Contributions are earmarks, never new income.", action = "Create goal", onAction = { onEdit(0) }) }
    }
    progress.sortedBy { it.completed }.forEach { p ->
        item(key = "g-${p.goal.id}") {
            val c = Eq.colors
            val tint = Color(p.goal.color)
            EqCard(onClick = { onOpen(p.goal.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(p.fraction, Modifier.size(64.dp), color = tint, thickness = 7.dp) {
                        IconBadge(AppIcons.of(p.goal.icon), tint, size = 36.dp, corner = 18.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.goal.name, style = Eq.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text("${p.percent}%", style = Eq.type.titleSmall, color = tint)
                        }
                        Text(
                            "${Money.format(p.saved, p.goal.currency)} of ${Money.format(p.goal.targetMinor, p.goal.currency)}",
                            style = Eq.type.bodySmall, color = c.textSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                p.completed -> "Goal reached 🎉"
                                p.monthlyNeeded != null && (p.daysLeft ?: 0) >= 0 -> "Save ${Money.format(p.monthlyNeeded, p.goal.currency)}/mo · ${p.daysLeft} days left"
                                p.daysLeft != null && p.daysLeft < 0 -> "Target date passed · ${Money.format(p.remaining, p.goal.currency)} to go"
                                else -> "${Money.format(p.remaining, p.goal.currency)} to go"
                            },
                            style = Eq.type.labelMedium, color = if (p.completed) c.income else c.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/* ================================================================== */
/* Safe-to-Spend detail                                                */
/* ================================================================== */

@Composable
fun SafeToSpendScreen(onBack: () -> Unit, onOpenBill: (Long) -> Unit, onOpenBudget: (Long) -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val c = Eq.colors
    val s = l.safeToSpend
    val hidden = l.settings.hideBalances
    val period = l.budgetPeriod
    val elapsed = (ChronoUnit.DAYS.between(period.start, l.today).toFloat() + 0.5f) / period.lengthDays
    var bufferText by remember(l.settings.bufferMinor) { mutableStateOf(Money.editable(l.settings.bufferMinor, l.base)) }
    var daySheet by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { EqTopBar("Safe to spend", onBack = onBack, subtitle = "${Fmt.short(period.start)} – ${Fmt.short(period.endInclusive)} · ${s.daysLeft} days left") }
        item {
            HeroSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        HorizonGauge(s.progress, Modifier.fillMaxWidth().aspectRatio(2f), timeFraction = elapsed.coerceIn(0f, 1f), thickness = 14.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Overline("Left for today", color = Color.White.copy(alpha = 0.7f))
                            MoneyText(s.leftToday.coerceAtLeast(0), l.base, style = Eq.type.displayMedium, color = if (s.leftToday < 0) c.expense else Color.White, hidden = hidden, animate = true)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (s.leftToday < 0) Tag("Overspent today by ${Money.format(-s.leftToday, l.base)}", c.expense)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        HeroStat("Daily allowance", s.daily, l.base, hidden, Modifier.weight(1f))
                        HeroStat("Next 7 days", s.weekly, l.base, hidden, Modifier.weight(1f))
                        HeroStat("Rest of period", s.remaining.coerceAtLeast(0), l.base, hidden, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            EqCard {
                Overline("How it's calculated")
                Spacer(Modifier.height(8.dp))
                CalcLine("Spendable account balances", s.input.spendableBalance, l.base, hidden, c.textPrimary)
                CalcLine("Unpaid bills due this period", -s.input.upcomingBills, l.base, hidden, c.expense)
                CalcLine("Money saved in goals", -s.input.goalReserve, l.base, hidden, c.expense, muted = !l.settings.protectGoals)
                CalcLine("Safety cushion", -s.input.buffer, l.base, hidden, c.expense)
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(c.stroke))
                CalcLine("Available after obligations", s.availableAfterReserves, l.base, hidden, c.textPrimary, bold = true)
                if (s.budgetRemaining != null) {
                    CalcLine("Overall budget remaining (after bills)", s.budgetRemaining, l.base, hidden, c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Tag(if (s.limitedBy == SafeToSpendLimit.BUDGET) "Limited by your budget" else "Limited by your balances", c.accent)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your account balances are never changed by this number. It is the discretionary money left once known obligations are set aside, spread evenly over the ${s.daysLeft} remaining days. Bill payments don't reduce today's allowance — they were already reserved.",
                    style = Eq.type.bodySmall, color = c.textSecondary,
                )
            }
        }
        item { SectionHeader("Tune it") }
        item {
            EqCard(padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                SettingRow(Icons.Rounded.Savings, "Protect goal savings", "Exclude money saved in goals", trailing = { EqSwitch(l.settings.protectGoals) { vm.setProtectGoals(it) } })
                SettingRow(Icons.Rounded.Event, "Budget month starts on day ${l.settings.periodStartDay}", "Match your payday", onClick = { daySheet = true })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EqTextField(bufferText, { bufferText = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Safety cushion", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    ChoiceChip("Apply", false, { vm.setBuffer(Money.parseToMinor(bufferText, l.base) ?: 0L) })
                }
                Spacer(Modifier.height(8.dp))
                if (l.overallMonthlyBudget == null) {
                    SettingRow(Icons.Rounded.Add, "Add an overall monthly budget", "Optional cap on discretionary spending", onClick = { onOpenBudget(0) })
                }
            }
        }
        val dues = l.billDues(period.endInclusive)
        if (dues.isNotEmpty()) {
            item { SectionHeader("Reserved for bills") }
            item { EqCard(padding = PaddingValues(8.dp)) { dues.forEach { BillDueRow(it, hidden) { onOpenBill(it.bill.id) } } } }
        }
    }
    if (daySheet) {
        PickerSheet("Budget month starts on", (1..28).map { PickerItem(it, "Day $it") }, l.settings.periodStartDay, { vm.setStartDay(it) }, { daySheet = false })
    }
}

@Composable
private fun HeroStat(label: String, amount: Long, base: String, hidden: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.6f))
        MoneyText(amount, base, style = Eq.type.titleSmall, color = Color.White, hidden = hidden, compact = true)
    }
}

@Composable
private fun CalcLine(label: String, amount: Long, base: String, hidden: Boolean, color: Color, bold: Boolean = false, muted: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (bold) Eq.type.titleSmall else Eq.type.bodyMedium, color = if (muted) Eq.colors.textTertiary else Eq.colors.textSecondary, modifier = Modifier.weight(1f))
        MoneyText(amount, base, style = if (bold) Eq.type.titleMedium else Eq.type.labelLarge, color = if (muted) Eq.colors.textTertiary else color, hidden = hidden)
    }
}

