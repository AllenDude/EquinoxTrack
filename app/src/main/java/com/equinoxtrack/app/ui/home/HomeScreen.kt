package com.equinoxtrack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.domain.BillDue
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.SafeToSpendResult
import com.equinoxtrack.app.domain.SeriesPoint
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.TransactionRow
import com.equinoxtrack.app.ui.common.TxVisual
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.common.visual
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.BarGroup
import com.equinoxtrack.app.ui.components.ChartLegend
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.DonutChart
import com.equinoxtrack.app.ui.components.EmptyState
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.GroupedBarChart
import com.equinoxtrack.app.ui.components.HeroSurface
import com.equinoxtrack.app.ui.components.HorizonGauge
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.ProgressTrack
import com.equinoxtrack.app.ui.components.Reveal
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.Slice
import com.equinoxtrack.app.ui.components.Tag
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class CategorySliceUi(val name: String, val color: Long, val icon: String, val amount: Long)
data class BudgetUi(val id: Long, val name: String, val icon: String, val color: Long, val spent: Long, val limit: Long, val fraction: Float, val over: Boolean, val pace: Float)

data class HomeState(
    val base: String,
    val hidden: Boolean,
    val greeting: String,
    val dateLabel: String,
    val totalBalance: Long,
    val netWorth: Long,
    val monthIncome: Long,
    val monthExpense: Long,
    val safe: SafeToSpendResult,
    val periodLabel: String,
    val timeFraction: Float,
    val history: List<SeriesPoint>,
    val categories: List<CategorySliceUi>,
    val budgets: List<BudgetUi>,
    val bills: List<BillDue>,
    val recent: List<Pair<Long, TxVisual>>,
    val missingRates: Set<String>,
    val hasAccounts: Boolean,
)

class HomeViewModel(c: AppContainer) : BaseViewModel(c) {
    val state: StateFlow<HomeState?> = ledger.filterNotNull()
        .map { build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleHidden(current: Boolean) {
        viewModelScope.launch { container.settings.setHideBalances(!current) }
    }

    private fun build(l: Ledger): HomeState {
        val month = Periods.month(YearMonth.from(l.today))
        val totals = l.totals(month)
        val hour = LocalTime.now().hour
        val greeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        val period = l.budgetPeriod
        val elapsed = (ChronoUnit.DAYS.between(period.start, l.today).toFloat() + 0.5f) / period.lengthDays.toFloat()

        val cats = l.byCategory(month, CategoryKind.EXPENSE)
        val top = cats.take(5).map { ca ->
            val cat = ca.categoryId?.let { l.categoriesById[it] }
            CategorySliceUi(cat?.name ?: "Uncategorized", cat?.color ?: 0xFF94A3B8, cat?.icon ?: "more", ca.amount)
        }
        val rest = cats.drop(5).sumOf { it.amount }
        val slices = if (rest > 0) top + CategorySliceUi("Other", 0xFF64748B, "more", rest) else top

        val budgets = l.budgetStatuses().sortedByDescending { it.fraction }.take(3).map { s ->
            val cat = s.budget.categoryId?.let { l.categoriesById[it] }
            val elapsedBudget = (ChronoUnit.DAYS.between(s.period.start, l.today) + 1).toFloat() / s.period.lengthDays
            BudgetUi(
                id = s.budget.id,
                name = cat?.name ?: "All spending",
                icon = cat?.icon ?: "trending",
                color = cat?.color ?: 0xFF7C5CFF,
                spent = s.spent,
                limit = s.limit,
                fraction = s.fraction,
                over = s.over,
                pace = elapsedBudget.coerceIn(0f, 1f),
            )
        }

        return HomeState(
            base = l.base,
            hidden = l.settings.hideBalances,
            greeting = greeting,
            dateLabel = Fmt.full(l.today),
            totalBalance = l.totalBalance,
            netWorth = l.netWorth.total,
            monthIncome = totals.income,
            monthExpense = totals.expense,
            safe = l.safeToSpend,
            periodLabel = "${Fmt.short(period.start)} – ${Fmt.short(period.endInclusive)}",
            timeFraction = elapsed.coerceIn(0f, 1f),
            history = l.monthlyHistory(YearMonth.from(l.today), 6),
            categories = slices,
            budgets = budgets,
            bills = l.upcomingBills.take(4),
            recent = l.data.transactions.take(6).map { it.tx.id to l.visual(it) },
            missingRates = l.missingRates,
            hasAccounts = l.data.accounts.any { !it.archived },
        )
    }
}

@Composable
fun HomeScreen(
    onAdd: (TxType) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeActivity: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenSafe: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenBills: () -> Unit,
    onOpenBill: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRates: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val vm = appViewModel { HomeViewModel(it) }
    VmEffects(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Eq.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.dateLabel.uppercase(), style = Eq.type.labelSmall, color = c.textTertiary)
                    Text(s.greeting, style = Eq.type.headlineMedium, color = c.textPrimary)
                }
                CircleIconButton(if (s.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Hide balances", { vm.toggleHidden(s.hidden) })
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }

        item { Reveal(0) { BalanceHero(s, onOpenWallet) } }

        if (s.missingRates.isNotEmpty()) item {
            EqCard(onClick = onOpenRates, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = c.warning)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Set exchange rates for ${s.missingRates.joinToString()} so totals include them.",
                        style = Eq.type.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textTertiary)
                }
            }
        }

        item { Reveal(1) { SafeToSpendCard(s, onOpenSafe) } }

        item {
            Reveal(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Expense", Icons.Rounded.Remove, c.expense, Modifier.weight(1f)) { onAdd(TxType.EXPENSE) }
                    QuickAction("Income", Icons.Rounded.Add, c.income, Modifier.weight(1f)) { onAdd(TxType.INCOME) }
                    QuickAction("Transfer", Icons.Rounded.SwapHoriz, c.transfer, Modifier.weight(1f)) { onAdd(TxType.TRANSFER) }
                    QuickAction("Bills", Icons.Rounded.Receipt, c.warning, Modifier.weight(1f), onOpenBills)
                }
            }
        }

        item { Reveal(3) { MonthSummaryCard(s, onOpenInsights) } }

        item { Reveal(4) { SpendingCard(s, onOpenInsights) } }

        item {
            Reveal(5) {
                Column {
                    SectionHeader("Budgets", action = "Manage", onAction = onOpenBudgets)
                    if (s.budgets.isEmpty()) {
                        EqCard(onClick = onOpenBudgets) {
                            Text("Set a monthly budget", style = Eq.type.titleSmall, color = c.textPrimary)
                            Text("Budgets keep Safe-to-Spend honest and show your pace through the month.", style = Eq.type.bodySmall, color = c.textSecondary)
                        }
                    } else EqCard(onClick = onOpenBudgets) {
                        s.budgets.forEachIndexed { i, b ->
                            if (i > 0) Spacer(Modifier.height(14.dp))
                            BudgetLine(b, s.base, s.hidden)
                        }
                    }
                }
            }
        }

        item {
            Reveal(6) {
                Column {
                    SectionHeader("Upcoming bills", action = "See all", onAction = onOpenBills)
                    if (s.bills.isEmpty()) {
                        EqCard(onClick = onOpenBills) {
                            Text("No bills in the next 30 days", style = Eq.type.titleSmall, color = c.textPrimary)
                            Text("Add recurring bills and subscriptions to plan ahead.", style = Eq.type.bodySmall, color = c.textSecondary)
                        }
                    } else EqCard(padding = PaddingValues(vertical = 8.dp, horizontal = 10.dp)) {
                        s.bills.forEach { due -> BillDueRow(due, s.hidden) { onOpenBill(due.bill.id) } }
                    }
                }
            }
        }

        item {
            Reveal(7) {
                Column {
                    SectionHeader("Recent activity", action = "See all", onAction = onSeeActivity)
                    if (s.recent.isEmpty()) {
                        EqCard {
                            EmptyState(
                                Icons.Rounded.Add,
                                if (s.hasAccounts) "No transactions yet" else "Create an account first",
                                "Tap + to record your first expense or income.",
                                action = "Add expense",
                                onAction = { onAdd(TxType.EXPENSE) },
                            )
                        }
                    } else EqCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                        s.recent.forEach { (id, v) -> TransactionRow(v, onClick = { onOpenTransaction(id) }, hidden = s.hidden) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(s: HomeState, onClick: () -> Unit) {
    HeroSurface(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(22.dp)) {
            Overline("Total balance", color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            MoneyText(s.totalBalance, s.base, style = Eq.type.displayMedium, color = Color.White, hidden = s.hidden, animate = true)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Net worth ", style = Eq.type.bodyMedium, color = Color.White.copy(alpha = 0.65f))
                MoneyText(s.netWorth, s.base, style = Eq.type.titleSmall, color = Color.White.copy(alpha = 0.9f), hidden = s.hidden)
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowPill("Income", s.monthIncome, s.base, Icons.Rounded.ArrowDownward, Eq.colors.income, s.hidden, Modifier.weight(1f))
                FlowPill("Spent", s.monthExpense, s.base, Icons.Rounded.ArrowUpward, Eq.colors.expense, s.hidden, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FlowPill(label: String, amount: Long, currency: String, icon: ImageVector, tint: Color, hidden: Boolean, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$label · month", style = Eq.type.labelSmall, color = Color.White.copy(alpha = 0.6f))
            MoneyText(amount, currency, style = Eq.type.titleSmall, color = Color.White, hidden = hidden, compact = true)
        }
    }
}

@Composable
private fun SafeToSpendCard(s: HomeState, onClick: () -> Unit) {
    val c = Eq.colors
    val safe = s.safe
    EqCard(onClick = onClick, padding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Safe to spend today")
                Text(s.periodLabel + " · ${safe.daysLeft} days left", style = Eq.type.bodySmall, color = c.textSecondary)
            }
            Tag(if (safe.leftToday < 0) "Over today" else "On track", if (safe.leftToday < 0) c.expense else c.income)
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.BottomCenter) {
            HorizonGauge(
                spentFraction = safe.progress,
                timeFraction = s.timeFraction,
                modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(2.05f),
                track = c.surfaceSunken,
                tickColor = c.textTertiary,
                sunColor = if (c.isDark) Color.White else c.accent,
                glowColor = c.accent,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                MoneyText(
                    safe.leftToday.coerceAtLeast(0), s.base,
                    style = Eq.type.displaySmall, color = if (safe.leftToday < 0) c.expense else c.textPrimary,
                    hidden = s.hidden, animate = true, textAlign = TextAlign.Center,
                )
                Text("of ${if (s.hidden) "••••" else com.equinoxtrack.app.domain.Money.format(safe.daily, s.base)} daily allowance", style = Eq.type.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            MiniStat("This week", safe.weekly, s.base, s.hidden, Modifier.weight(1f))
            MiniStat("Discretionary left", safe.remaining.coerceAtLeast(0), s.base, s.hidden, Modifier.weight(1.3f))
            MiniStat("Bills reserved", safe.input.upcomingBills, s.base, s.hidden, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, amount: Long, currency: String, hidden: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Eq.type.labelMedium, color = Eq.colors.textTertiary, maxLines = 1)
        MoneyText(amount, currency, style = Eq.type.titleSmall, hidden = hidden, compact = true)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Eq.colors
    Column(
        modifier
            .pressable(onClick, RoundedCornerShape(20.dp))
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.stroke, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, tint, size = 40.dp, corner = 13.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, style = Eq.type.labelMedium, color = c.textPrimary)
    }
}

@Composable
private fun MonthSummaryCard(s: HomeState, onClick: () -> Unit) {
    val c = Eq.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    val net = s.monthIncome - s.monthExpense
    val rate = if (s.monthIncome > 0) (net * 100 / s.monthIncome) else 0L
    EqCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Overline("This month")
                MoneyText(net, s.base, style = Eq.type.headlineMedium, color = if (net >= 0) c.textPrimary else c.expense, signed = true, hidden = s.hidden)
                Text("net cash flow", style = Eq.type.bodySmall, color = c.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$rate%", style = Eq.type.headlineSmall, color = if (rate >= 0) c.income else c.expense)
                Text("saved", style = Eq.type.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        val sel = selected
        if (sel != null && sel in s.history.indices) {
            val p = s.history[sel]
            Text(
                "${p.label}: in ${com.equinoxtrack.app.domain.Money.format(p.income, s.base, compact = true)} · out ${com.equinoxtrack.app.domain.Money.format(p.expense, s.base, compact = true)}",
                style = Eq.type.labelMedium, color = c.textPrimary,
            )
        } else ChartLegend(listOf(c.income to "Income", c.expense to "Expenses"))
        Spacer(Modifier.height(10.dp))
        GroupedBarChart(
            groups = s.history.map { BarGroup(it.label, it.income, it.expense) },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            selected = selected,
            onSelect = { selected = it },
        )
    }
}

@Composable
private fun SpendingCard(s: HomeState, onClick: () -> Unit) {
    val c = Eq.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    Column {
        SectionHeader("Spending overview", action = "Insights", onAction = onClick)
        EqCard {
            if (s.categories.isEmpty()) {
                Text("No spending yet this month", style = Eq.type.titleSmall, color = c.textPrimary)
                Text("Your category breakdown will appear here.", style = Eq.type.bodySmall, color = c.textSecondary)
            } else {
                val total = s.categories.sumOf { it.amount }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = s.categories.map { Slice(it.amount.toFloat(), Color(it.color)) },
                        modifier = Modifier.size(150.dp),
                        selected = selected,
                        onSelect = { selected = it },
                        thickness = 16.dp,
                    ) {
                        val sel = selected
                        val label = if (sel != null) s.categories[sel].name else "Spent"
                        val amount = if (sel != null) s.categories[sel].amount else total
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = Eq.type.labelMedium, color = c.textSecondary, maxLines = 1)
                            MoneyText(amount, s.base, style = Eq.type.titleMedium, hidden = s.hidden, compact = true)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.categories.forEachIndexed { i, cat ->
                            Row(
                                Modifier.clip(RoundedCornerShape(10.dp)).pressable({ selected = if (selected == i) null else i }, RoundedCornerShape(10.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(cat.color)))
                                Spacer(Modifier.width(8.dp))
                                Text(cat.name, style = Eq.type.bodySmall, color = if (selected == null || selected == i) c.textPrimary else c.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
                                Text("${if (total > 0) cat.amount * 100 / total else 0}%", style = Eq.type.labelMedium, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetLine(b: BudgetUi, base: String, hidden: Boolean) {
    val c = Eq.colors
    val tint = when {
        b.over -> c.expense
        b.fraction > b.pace + 0.1f -> c.warning
        else -> Color(b.color)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(AppIcons.of(b.icon), Color(b.color), size = 36.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, style = Eq.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                MoneyText(b.spent, base, style = Eq.type.labelLarge, hidden = hidden, compact = true)
                Text(" / ", style = Eq.type.labelMedium, color = c.textTertiary)
                MoneyText(b.limit, base, style = Eq.type.labelMedium, color = c.textTertiary, hidden = hidden, compact = true)
            }
            Spacer(Modifier.height(7.dp))
            ProgressTrack(b.fraction, color = tint, marker = b.pace)
        }
    }
}

@Composable
fun BillDueRow(due: BillDue, hidden: Boolean, onClick: () -> Unit) {
    val c = Eq.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick, RoundedCornerShape(16.dp)).padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(46.dp).clip(RoundedCornerShape(14.dp)).background(if (due.overdue) c.expense.copy(alpha = 0.14f) else c.surfaceSunken).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(due.dueDate.month.name.take(3), style = Eq.type.labelSmall, color = if (due.overdue) c.expense else c.textTertiary)
            Text(due.dueDate.dayOfMonth.toString(), style = Eq.type.titleMedium, color = if (due.overdue) c.expense else c.textPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(due.bill.name, style = Eq.type.titleSmall, color = c.textPrimary, maxLines = 1)
            Text(Fmt.dueLabel(due.daysUntil), style = Eq.type.bodySmall, color = if (due.overdue) c.expense else c.textSecondary)
        }
        MoneyText(due.amountMinor, due.bill.currency, hidden = hidden)
    }
}
