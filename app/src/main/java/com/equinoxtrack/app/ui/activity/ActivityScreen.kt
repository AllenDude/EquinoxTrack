package com.equinoxtrack.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.DateRange
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.TransactionRow
import com.equinoxtrack.app.ui.common.TxVisual
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.common.visual
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.EmptyState
import com.equinoxtrack.app.ui.components.EqDatePickerDialog
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.SearchField
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class RangePreset(val label: String) { ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), LAST_30("Last 30 days"), THIS_YEAR("This year"), CUSTOM("Custom") }
enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Expenses"), INCOME("Income"), TRANSFER("Transfers") }

data class ActivityFilters(
    val query: String = "",
    val type: TypeFilter = TypeFilter.ALL,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val preset: RangePreset = RangePreset.ALL,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
)

data class DayGroup(val date: LocalDate, val label: String, val net: Long, val items: List<Pair<Long, TxVisual>>)

data class ActivityState(
    val filters: ActivityFilters,
    val groups: List<DayGroup>,
    val count: Int,
    val income: Long,
    val expense: Long,
    val base: String,
    val hidden: Boolean,
    val accounts: List<PickerItem<Long?>>,
    val categories: List<PickerItem<Long?>>,
    val accountName: String?,
    val categoryName: String?,
    val rangeLabel: String,
)

class ActivityViewModel(c: AppContainer, initialAccount: Long?) : BaseViewModel(c) {
    private val filters = MutableStateFlow(ActivityFilters(accountId = initialAccount))

    val state: StateFlow<ActivityState?> = combine(ledger.filterNotNull(), filters.debounce(120)) { l, f -> build(l, f) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val current: StateFlow<ActivityFilters> = filters

    fun update(block: (ActivityFilters) -> ActivityFilters) = filters.update(block)

    private fun range(f: ActivityFilters, today: LocalDate): DateRange? = when (f.preset) {
        RangePreset.ALL -> null
        RangePreset.THIS_MONTH -> Periods.month(YearMonth.from(today))
        RangePreset.LAST_MONTH -> Periods.month(YearMonth.from(today).minusMonths(1))
        RangePreset.LAST_30 -> DateRange(today.minusDays(29), today)
        RangePreset.THIS_YEAR -> Periods.year(today.year)
        RangePreset.CUSTOM -> DateRange(f.customStart ?: today.minusMonths(1), f.customEnd ?: today)
    }

    private fun build(l: Ledger, f: ActivityFilters): ActivityState {
        val r = range(f, l.today)
        val q = f.query.trim().lowercase()
        val filtered = l.data.transactions.filter { t ->
            val tx = t.tx
            val typeOk = when (f.type) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> tx.type == TxType.EXPENSE
                TypeFilter.INCOME -> tx.type == TxType.INCOME
                TypeFilter.TRANSFER -> tx.type == TxType.TRANSFER
            }
            if (!typeOk) return@filter false
            if (f.accountId != null && tx.accountId != f.accountId && tx.toAccountId != f.accountId) return@filter false
            if (f.categoryId != null && t.lines.none { it.categoryId == f.categoryId }) return@filter false
            if (r != null && l.dateOf(t) !in r) return@filter false
            if (q.isNotEmpty()) {
                val cur = l.currencyOf(tx.accountId)
                val hay = buildString {
                    append(l.titleOf(t)).append(' ').append(l.subtitleOf(t)).append(' ')
                    t.lines.forEach { append(l.categoryName(it.categoryId)).append(' ') }
                    append(Money.plain(tx.amountMinor, cur)).append(' ').append(Money.format(tx.amountMinor, cur))
                }.lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }
        var income = 0L
        var expense = 0L
        filtered.forEach { t ->
            val cur = l.currencyOf(t.tx.accountId)
            when (t.tx.type) {
                TxType.INCOME -> income += l.toBase(t.tx.amountMinor, cur)
                TxType.EXPENSE -> expense += l.toBase(t.tx.amountMinor, cur)
                else -> Unit
            }
        }
        val groups = filtered.groupBy { l.dateOf(it) }.entries.sortedByDescending { it.key }.map { (date, list) ->
            val net = list.sumOf { t ->
                val cur = l.currencyOf(t.tx.accountId)
                when (t.tx.type) {
                    TxType.INCOME -> l.toBase(t.tx.amountMinor, cur)
                    TxType.EXPENSE -> -l.toBase(t.tx.amountMinor, cur)
                    else -> 0L
                }
            }
            DayGroup(date, Fmt.dayHeader(date, l.today), net, list.map { it.tx.id to l.visual(it, f.accountId) })
        }
        val accounts = listOf(PickerItem<Long?>(null, "All accounts")) + l.data.accounts.map {
            PickerItem<Long?>(it.id, it.name, it.type.label + if (it.archived) " · archived" else "", AppIcons.of(it.icon), Color(it.color))
        }
        val categories = listOf(PickerItem<Long?>(null, "All categories")) + l.data.categories.sortedBy { it.kind }.map {
            PickerItem<Long?>(it.id, it.name, if (it.kind == CategoryKind.INCOME) "Income" else "Expense", AppIcons.of(it.icon), Color(it.color))
        }
        val rangeLabel = when (f.preset) {
            RangePreset.CUSTOM -> r?.let { "${Fmt.short(it.start)} – ${Fmt.short(it.endInclusive)}" } ?: "Custom"
            else -> f.preset.label
        }
        return ActivityState(
            filters = f, groups = groups, count = filtered.size, income = income, expense = expense, base = l.base,
            hidden = l.settings.hideBalances, accounts = accounts, categories = categories,
            accountName = f.accountId?.let { l.accountsById[it]?.name },
            categoryName = f.categoryId?.let { l.categoriesById[it]?.name },
            rangeLabel = rangeLabel,
        )
    }
}

@Composable
fun ActivityScreen(
    onOpenTransaction: (Long) -> Unit,
    bottomPadding: PaddingValues,
    initialAccount: Long? = null,
    onBack: (() -> Unit)? = null,
) {
    val vm = appViewModel(key = "activity-$initialAccount") { ActivityViewModel(it, initialAccount) }
    VmEffects(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.current.collectAsStateWithLifecycle()
    val c = Eq.colors
    var sheet by remember { mutableStateOf<String?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        EqTopBar("Activity", onBack = onBack, subtitle = state?.let { "${it.count} transactions" })
        Column(Modifier.padding(horizontal = 18.dp)) {
            SearchField(filters.query, { q -> vm.update { it.copy(query = q) } }, "Notes, categories, accounts, amounts")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { t ->
                    ChoiceChip(t.label, filters.type == t, { vm.update { it.copy(type = t) } })
                }
                ChoiceChip(state?.rangeLabel ?: filters.preset.label, filters.preset != RangePreset.ALL, { sheet = "range" }, icon = Icons.Rounded.DateRange)
                ChoiceChip(state?.accountName ?: "Account", filters.accountId != null, { sheet = "account" }, icon = Icons.Rounded.AccountBalanceWallet)
                ChoiceChip(state?.categoryName ?: "Category", filters.categoryId != null, { sheet = "category" }, icon = Icons.Rounded.Category)
                if (filters != ActivityFilters(accountId = initialAccount)) {
                    ChoiceChip("Clear", false, { vm.update { ActivityFilters(accountId = initialAccount) } }, icon = Icons.Rounded.Close, tint = c.expense)
                }
            }
        }
        val s = state
        if (s != null) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SummaryCell("In", s.income, s.base, c.income, s.hidden, Modifier.weight(1f))
                SummaryCell("Out", s.expense, s.base, c.expense, s.hidden, Modifier.weight(1f))
                SummaryCell("Net", s.income - s.expense, s.base, c.textPrimary, s.hidden, Modifier.weight(1f))
            }
            if (s.groups.isEmpty()) {
                EmptyState(Icons.Rounded.Search, "Nothing here", "No transactions match these filters.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
                ) {
                    s.groups.forEach { g ->
                        stickyHeader(key = "h-${g.date}") {
                            Row(
                                Modifier.fillMaxWidth().background(c.background).padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Overline(g.label, Modifier.weight(1f), color = c.textSecondary)
                                MoneyText(g.net, s.base, style = Eq.type.labelMedium, color = c.textTertiary, signed = true, hidden = s.hidden)
                            }
                        }
                        items(g.items, key = { it.first }) { (id, v) ->
                            TransactionRow(v, onClick = { onOpenTransaction(id) }, hidden = s.hidden)
                        }
                    }
                }
            }

            when (sheet) {
                "account" -> PickerSheet("Account", s.accounts, filters.accountId, { id -> vm.update { it.copy(accountId = id) } }, { sheet = null })
                "category" -> PickerSheet("Category", s.categories, filters.categoryId, { id -> vm.update { it.copy(categoryId = id) } }, { sheet = null }, searchable = true)
                "range" -> PickerSheet(
                    "Period",
                    RangePreset.entries.map { PickerItem(it, it.label) },
                    filters.preset,
                    { p ->
                        vm.update { it.copy(preset = p) }
                        if (p == RangePreset.CUSTOM) pickStart = true
                    },
                    { sheet = null },
                )
            }
        }
    }
    if (pickStart) {
        EqDatePickerDialog(filters.customStart ?: LocalDate.now().minusMonths(1), { pickStart = false }) { d ->
            vm.update { it.copy(customStart = d) }
            pickEnd = true
        }
    }
    if (pickEnd) {
        EqDatePickerDialog(filters.customEnd ?: LocalDate.now(), { pickEnd = false }) { d ->
            vm.update { it.copy(customEnd = d) }
        }
    }
}

@Composable
private fun SummaryCell(label: String, amount: Long, base: String, color: Color, hidden: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Eq.type.labelMedium, color = Eq.colors.textTertiary)
        MoneyText(amount, base, style = Eq.type.titleSmall, color = color, hidden = hidden, compact = true)
    }
}
