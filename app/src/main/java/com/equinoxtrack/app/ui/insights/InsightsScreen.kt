package com.equinoxtrack.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.DateRange
import com.equinoxtrack.app.domain.FlowTotals
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.PeriodKind
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.SeriesPoint
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.BarGroup
import com.equinoxtrack.app.ui.components.ChartLegend
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.DonutChart
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.GroupedBarChart
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.ProgressTrack
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.Slice
import com.equinoxtrack.app.ui.components.TrendLine
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class InsightsQuery(val kind: PeriodKind = PeriodKind.MONTH, val offset: Long = 0, val accountId: Long? = null, val incomeCategories: Boolean = false)

data class CategoryRow(val id: Long?, val name: String, val icon: String, val color: Long, val amount: Long, val share: Float)

data class InsightsState(
    val query: InsightsQuery,
    val base: String,
    val hidden: Boolean,
    val range: DateRange,
    val label: String,
    val totals: FlowTotals,
    val previous: FlowTotals,
    val series: List<SeriesPoint>,
    val categories: List<CategoryRow>,
    val trend: List<Long>,
    val trendLabels: List<String>,
    val history: List<SeriesPoint>,
    val avgDaily: Long,
    val biggest: Pair<String, Long>?,
    val goalSaved: Long,
    val accountName: String?,
    val accounts: List<PickerItem<Long?>>,
)

class InsightsViewModel(c: AppContainer) : BaseViewModel(c) {
    private val query = MutableStateFlow(InsightsQuery())
    val state: StateFlow<InsightsState?> = combine(ledger.filterNotNull(), query) { l, q -> build(l, q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(block: (InsightsQuery) -> InsightsQuery) = query.update(block)

    private fun label(kind: PeriodKind, r: DateRange, today: LocalDate): String = when (kind) {
        PeriodKind.WEEK -> if (today in r) "This week" else "${Fmt.short(r.start)} – ${Fmt.short(r.endInclusive)}"
        PeriodKind.MONTH -> r.start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        PeriodKind.YEAR -> r.start.year.toString()
    }

    private fun build(l: Ledger, q: InsightsQuery): InsightsState {
        val anchor = Periods.shift(q.kind, l.today, q.offset)
        val range = Periods.period(q.kind, anchor)
        val prevRange = Periods.period(q.kind, Periods.shift(q.kind, anchor, -1))
        val totals = l.totals(range, q.accountId)
        val previous = l.totals(prevRange, q.accountId)
        val kind = if (q.incomeCategories) CategoryKind.INCOME else CategoryKind.EXPENSE
        val cats = l.byCategory(range, kind, q.accountId)
        val catTotal = cats.sumOf { it.amount }.coerceAtLeast(1)
        val rows = cats.map { ca ->
            val cat = ca.categoryId?.let { l.categoriesById[it] }
            CategoryRow(ca.categoryId, cat?.name ?: "Uncategorized", cat?.icon ?: "more", cat?.color ?: 0xFF94A3B8, ca.amount, ca.amount.toFloat() / catTotal)
        }
        val trendRanges = (5 downTo 0).map { back -> Periods.period(q.kind, Periods.shift(q.kind, anchor, -back.toLong())) }
        val trend = trendRanges.map { l.totals(it, q.accountId).expense }
        val trendLabels = trendRanges.map {
            when (q.kind) {
                PeriodKind.WEEK -> Fmt.short(it.start)
                PeriodKind.MONTH -> it.start.month.name.take(3).lowercase().replaceFirstChar { ch -> ch.uppercase() }
                PeriodKind.YEAR -> it.start.year.toString()
            }
        }
        val elapsedDays = if (l.today in range) range.lengthDays - range.daysLeftFrom(l.today) + 1 else range.lengthDays
        val biggest = l.transactionsIn(range, q.accountId).filter { it.tx.type == TxType.EXPENSE && (q.accountId == null || it.tx.accountId == q.accountId) }
            .maxByOrNull { l.toBase(it.tx.amountMinor, l.currencyOf(it.tx.accountId)) }
            ?.let { l.titleOf(it) + (if (it.tx.note.isNotBlank()) " · ${it.tx.note}" else "") to l.toBase(it.tx.amountMinor, l.currencyOf(it.tx.accountId)) }
        val accounts = listOf(PickerItem<Long?>(null, "All accounts")) + l.data.accounts.map {
            PickerItem<Long?>(it.id, it.name, it.type.label, AppIcons.of(it.icon), Color(it.color))
        }
        return InsightsState(
            query = q, base = l.base, hidden = l.settings.hideBalances, range = range, label = label(q.kind, range, l.today),
            totals = totals, previous = previous, series = l.series(q.kind, range, q.accountId), categories = rows,
            trend = trend, trendLabels = trendLabels,
            history = l.monthlyHistory(YearMonth.from(l.today), 12, q.accountId).reversed(),
            avgDaily = if (elapsedDays > 0) totals.expense / elapsedDays else 0,
            biggest = biggest,
            goalSaved = l.goalReserve,
            accountName = q.accountId?.let { l.accountsById[it]?.name },
            accounts = accounts,
        )
    }
}

@Composable
fun InsightsScreen(bottomPadding: PaddingValues) {
    val vm = appViewModel { InsightsViewModel(it) }
    VmEffects(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Eq.colors
    var accountSheet by remember { mutableStateOf(false) }
    var barSel by remember(s.range, s.query.accountId) { mutableStateOf<Int?>(null) }
    var donutSel by remember(s.range, s.query) { mutableStateOf<Int?>(null) }
    var trendSel by remember(s.range, s.query.accountId) { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            EqTopBar("Insights", subtitle = s.accountName ?: "All accounts") {
                CircleIconButton(Icons.Rounded.AccountBalanceWallet, "Account filter", { accountSheet = true }, tint = if (s.query.accountId != null) c.accent else c.textPrimary)
            }
        }
        item {
            SegmentedControl(PeriodKind.entries.toList(), s.query.kind, { k -> vm.update { it.copy(kind = k, offset = 0) } }, label = { it.label })
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.ChevronLeft, "Previous", { vm.update { it.copy(offset = it.offset - 1) } }, size = 36.dp)
                Text(s.label, style = Eq.type.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.ChevronRight, "Next", { vm.update { it.copy(offset = (it.offset + 1).coerceAtMost(0)) } }, size = 36.dp, tint = if (s.query.offset < 0) c.textPrimary else c.textTertiary)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Income", s.totals.income, s.previous.income, s.base, c.income, s.hidden, Modifier.weight(1f), higherIsGood = true)
                    Kpi("Expenses", s.totals.expense, s.previous.expense, s.base, c.expense, s.hidden, Modifier.weight(1f), higherIsGood = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Net", s.totals.net, s.previous.net, s.base, c.textPrimary, s.hidden, Modifier.weight(1f), higherIsGood = true)
                    EqCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) {
                        Text("Savings rate", style = Eq.type.labelMedium, color = c.textTertiary)
                        Text(Fmt.percent(s.totals.savingsRate), style = Eq.type.headlineSmall, color = if (s.totals.savingsRate >= 0) c.income else c.expense)
                        Spacer(Modifier.height(6.dp))
                        ProgressTrack(s.totals.savingsRate.coerceIn(0f, 1f), color = c.income, height = 6.dp)
                    }
                }
            }
        }
        item {
            EqCard {
                Overline("Income vs expenses")
                Spacer(Modifier.height(6.dp))
                val sel = barSel
                if (sel != null && sel in s.series.indices) {
                    val p = s.series[sel]
                    val name = if (p.range.start == p.range.endInclusive) Fmt.short(p.range.start) else p.range.start.format(DateTimeFormatter.ofPattern("MMMM"))
                    Text("$name · in ${Money.format(p.income, s.base)} · out ${Money.format(p.expense, s.base)}", style = Eq.type.labelLarge, color = c.textPrimary)
                } else ChartLegend(listOf(c.income to "Income", c.expense to "Expenses"))
                Spacer(Modifier.height(10.dp))
                GroupedBarChart(
                    s.series.map { BarGroup(it.label, it.income, it.expense) },
                    Modifier.fillMaxWidth().height(190.dp),
                    selected = barSel, onSelect = { barSel = it },
                    labelEvery = when (s.query.kind) { PeriodKind.MONTH -> 5; else -> 1 },
                )
            }
        }
        item {
            EqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline(if (s.query.incomeCategories) "Income by category" else "Spending by category", Modifier.weight(1f))
                    ChoiceChip(if (s.query.incomeCategories) "Income" else "Expenses", true, { vm.update { it.copy(incomeCategories = !it.incomeCategories) } })
                }
                Spacer(Modifier.height(12.dp))
                if (s.categories.isEmpty()) {
                    Text("Nothing recorded in this period.", style = Eq.type.bodyMedium, color = c.textSecondary)
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(
                            s.categories.map { Slice(it.amount.toFloat(), Color(it.color)) },
                            Modifier.size(210.dp), selected = donutSel, onSelect = { donutSel = it }, thickness = 20.dp,
                        ) {
                            val sel = donutSel
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (sel != null) s.categories[sel].name else "Total", style = Eq.type.labelMedium, color = c.textSecondary, maxLines = 1)
                                MoneyText(if (sel != null) s.categories[sel].amount else s.categories.sumOf { it.amount }, s.base, style = Eq.type.titleLarge, hidden = s.hidden, compact = true)
                                if (sel != null) Text(Fmt.percent(s.categories[sel].share), style = Eq.type.labelMedium, color = c.textTertiary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    s.categories.forEachIndexed { i, row ->
                        Row(
                            Modifier.fillMaxWidth().pressable({ donutSel = if (donutSel == i) null else i }, RoundedCornerShape(14.dp))
                                .background(if (donutSel == i) c.accentSoft else Color.Transparent).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(AppIcons.of(row.icon), Color(row.color), size = 34.dp, corner = 11.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(row.name, style = Eq.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                                    MoneyText(row.amount, s.base, style = Eq.type.labelLarge, hidden = s.hidden)
                                }
                                Spacer(Modifier.height(5.dp))
                                ProgressTrack(row.share, color = Color(row.color), height = 5.dp)
                            }
                        }
                    }
                }
            }
        }
        item {
            EqCard {
                Overline("Spending trend")
                val sel = trendSel
                Text(
                    if (sel != null && sel in s.trend.indices) "${s.trendLabels[sel]} · ${Money.format(s.trend[sel], s.base)}" else "Last 6 ${s.query.kind.label.lowercase()}s",
                    style = Eq.type.labelLarge, color = c.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                TrendLine(s.trend, Modifier.fillMaxWidth().height(130.dp), color = c.expense, selected = trendSel, onSelect = { trendSel = it })
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    s.trendLabels.forEachIndexed { i, lbl ->
                        Text(lbl, style = Eq.type.labelSmall, color = if (i == trendSel) c.textPrimary else c.textTertiary, modifier = Modifier.weight(1f), textAlign = when (i) { 0 -> TextAlign.Start; s.trendLabels.lastIndex -> TextAlign.End; else -> TextAlign.Center }, maxLines = 1)
                    }
                }
            }
        }
        item {
            EqCard {
                Overline("Highlights")
                Spacer(Modifier.height(10.dp))
                Highlight("Average daily spending", Money.format(s.avgDaily, s.base), s.hidden)
                val change = if (s.previous.expense > 0) ((s.totals.expense - s.previous.expense) * 100 / s.previous.expense) else null
                Highlight(
                    "Spending vs previous ${s.query.kind.label.lowercase()}",
                    change?.let { (if (it > 0) "+" else "") + "$it%" } ?: "—",
                    false,
                    color = when { change == null -> c.textPrimary; change > 0 -> c.expense; else -> c.income },
                )
                s.biggest?.let { Highlight("Largest expense", "${Money.format(it.second, s.base)} · ${it.first}", s.hidden) }
                Highlight("Net saved this period", Money.format(s.totals.net, s.base, signed = true), s.hidden, color = if (s.totals.net >= 0) c.income else c.expense)
                Highlight("Saved toward goals (all time)", Money.format(s.goalSaved, s.base), s.hidden)
            }
        }
        item { SectionHeader("Monthly history") }
        item {
            EqCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    listOf("Month", "In", "Out", "Net").forEachIndexed { i, h ->
                        Text(h, style = Eq.type.labelSmall, color = c.textTertiary, modifier = Modifier.weight(if (i == 0) 0.8f else 1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                    }
                }
                s.history.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(0.8f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (p.income - p.expense >= 0) c.income else c.expense))
                            Spacer(Modifier.width(8.dp))
                            Text("${p.label} ${p.range.start.year % 100}", style = Eq.type.bodyMedium, color = c.textPrimary)
                        }
                        Text(if (s.hidden) "••••" else Money.format(p.income, s.base, compact = true), style = Eq.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(if (s.hidden) "••••" else Money.format(p.expense, s.base, compact = true), style = Eq.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(if (s.hidden) "••••" else Money.format(p.income - p.expense, s.base, signed = true, compact = true), style = Eq.type.labelLarge, color = if (p.income - p.expense >= 0) c.income else c.expense, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
    if (accountSheet) PickerSheet("Analyze account", s.accounts, s.query.accountId, { id -> vm.update { it.copy(accountId = id) } }, { accountSheet = false })
}

@Composable
private fun Kpi(label: String, value: Long, previous: Long, base: String, color: Color, hidden: Boolean, modifier: Modifier, higherIsGood: Boolean) {
    val c = Eq.colors
    EqCard(modifier, padding = PaddingValues(14.dp)) {
        Text(label, style = Eq.type.labelMedium, color = c.textTertiary)
        MoneyText(value, base, style = Eq.type.headlineSmall, color = color, hidden = hidden, compact = true, animate = true)
        if (previous != 0L) {
            val pct = (value - previous) * 100 / kotlin.math.abs(previous)
            val good = if (higherIsGood) pct >= 0 else pct <= 0
            Text("${if (pct >= 0) "▲" else "▼"} ${kotlin.math.abs(pct)}% vs prev", style = Eq.type.labelSmall, color = if (good) c.income else c.expense)
        } else Text("No prior data", style = Eq.type.labelSmall, color = c.textTertiary)
    }
}

@Composable
private fun Highlight(label: String, value: String, hidden: Boolean, color: Color = Eq.colors.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Eq.type.bodyMedium, color = Eq.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(if (hidden) "••••" else value, style = Eq.type.labelLarge, color = color, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
