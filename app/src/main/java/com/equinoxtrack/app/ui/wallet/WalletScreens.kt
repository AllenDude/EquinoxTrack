package com.equinoxtrack.app.ui.wallet

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.AssetKind
import com.equinoxtrack.app.domain.BalanceCalculator
import com.equinoxtrack.app.domain.CurrencyCatalog
import com.equinoxtrack.app.domain.DateRange
import com.equinoxtrack.app.domain.Defaults
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.TransactionRow
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.common.visual
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.ConfirmDialog
import com.equinoxtrack.app.ui.components.EmptyState
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.EqDatePickerDialog
import com.equinoxtrack.app.ui.components.EqSwitch
import com.equinoxtrack.app.ui.components.EqTextField
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.HeroSurface
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.IconPicker
import com.equinoxtrack.app.ui.components.LegendItem
import com.equinoxtrack.app.ui.components.MoneyText
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.PrimaryButton
import com.equinoxtrack.app.ui.components.SecondaryButton
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.SettingRow
import com.equinoxtrack.app.ui.components.Slice
import com.equinoxtrack.app.ui.components.StackedBar
import com.equinoxtrack.app.ui.components.SwatchPicker
import com.equinoxtrack.app.ui.components.TrendLine
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/* ================================================================== */
/* Wallet overview: net worth, accounts, assets                        */
/* ================================================================== */

class WalletViewModel(c: AppContainer) : BaseViewModel(c) {
    val state: StateFlow<Ledger?> = ledger
}

@Composable
fun WalletScreen(
    onOpenAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onOpenAsset: (Long) -> Unit,
    onAddAsset: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val vm = appViewModel { WalletViewModel(it) }
    VmEffects(vm)
    val ledger by vm.state.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val c = Eq.colors
    val hidden = l.settings.hideBalances
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val nw = l.netWorth
    val typeColors = mapOf(AccountType.CASH to c.income, AccountType.BANK to c.transfer, AccountType.EWALLET to c.accent, AccountType.CUSTOM to c.warning)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { EqTopBar("Wallet", subtitle = "Accounts, assets and net worth") }
        item {
            HeroSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Overline("Net worth", color = Color.White.copy(alpha = 0.7f))
                    MoneyText(nw.total, l.base, style = Eq.type.displaySmall, color = Color.White, hidden = hidden, animate = true)
                    Text("Money in accounts plus tracked assets. Savings goals live inside your accounts, so they are never counted twice.", style = Eq.type.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                    val parts = AccountType.entries.map { Slice(nw.byType.getValue(it).coerceAtLeast(0).toFloat(), typeColors.getValue(it)) } +
                        Slice(nw.assetsTotal.coerceAtLeast(0).toFloat(), c.accentAlt)
                    StackedBar(parts)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccountType.entries.take(2).forEach { t -> BreakdownLine(typeColors.getValue(t), t.label, nw.byType.getValue(t), l.base, hidden) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccountType.entries.drop(2).forEach { t -> BreakdownLine(typeColors.getValue(t), t.label, nw.byType.getValue(t), l.base, hidden) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BreakdownLine(c.accentAlt, "Personal assets", nw.assetsTotal, l.base, hidden)
                }
            }
        }

        item { SectionHeader("Accounts", action = "Add", onAction = onAddAccount) }
        val visible = l.data.accounts.filter { showArchived || !it.archived }.sortedWith(compareBy({ it.archived }, { it.type.ordinal }, { it.sortOrder }, { it.name.lowercase() }))
        if (visible.isEmpty()) item {
            EqCard { EmptyState(Icons.Rounded.Add, "No accounts yet", "Add Cash, a bank account or an e-wallet to start tracking.", action = "Add account", onAction = onAddAccount) }
        }
        visible.forEach { a ->
            item(key = "a-${a.id}") {
                AccountCard(a, l.balance(a.id), hidden, l.base, l.converter.convert(l.balance(a.id), a.currency, l.base)) { onOpenAccount(a.id) }
            }
        }
        if (l.data.accounts.any { it.archived }) item {
            ChoiceChip(if (showArchived) "Hide archived" else "Show archived", showArchived, { showArchived = !showArchived }, icon = Icons.Rounded.Archive)
        }

        item { SectionHeader("Personal assets", action = "Add", onAction = onAddAsset) }
        val assets = l.data.assets.filter { !it.archived }
        if (assets.isEmpty()) item {
            EqCard(onClick = onAddAsset) {
                Text("Track what you own", style = Eq.type.titleSmall, color = c.textPrimary)
                Text("Home, vehicle, valuables… values you update manually. They add to net worth only, never to spendable money.", style = Eq.type.bodySmall, color = c.textSecondary)
            }
        } else item {
            EqCard(padding = PaddingValues(8.dp)) {
                assets.forEach { asset ->
                    Row(
                        Modifier.fillMaxWidth().pressable({ onOpenAsset(asset.id) }, RoundedCornerShape(16.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(AppIcons.asset(asset.kind), Color(asset.color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, style = Eq.type.titleSmall, color = c.textPrimary)
                            Text(asset.kind.label + if (!asset.includeInNetWorth) " · excluded" else "", style = Eq.type.bodySmall, color = c.textSecondary)
                        }
                        MoneyText(nw.assetValues[asset.id] ?: 0L, asset.currency, hidden = hidden)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownLine(color: Color, label: String, amount: Long, base: String, hidden: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color, label)
        Spacer(Modifier.width(6.dp))
        MoneyText(amount, base, style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.85f), hidden = hidden, compact = true)
    }
}

@Composable
private fun AccountCard(a: AccountEntity, balance: Long, hidden: Boolean, base: String, converted: Long?, onClick: () -> Unit) {
    val c = Eq.colors
    val tint = Color(a.color)
    EqCard(onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.of(a.icon), tint, size = 46.dp, corner = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = Eq.type.titleMedium, color = if (a.archived) c.textTertiary else c.textPrimary)
                val flags = buildList {
                    add(a.type.label)
                    if (a.currency != base) add(a.currency)
                    if (!a.includeInTotals) add("Not in totals")
                    else if (!a.includeInSafeToSpend) add("Not spendable")
                    if (a.archived) add("Archived")
                }
                Text(flags.joinToString(" · "), style = Eq.type.bodySmall, color = c.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(balance, a.currency, style = Eq.type.titleMedium, color = if (balance < 0) c.expense else c.textPrimary, hidden = hidden)
                if (a.currency != base && converted != null) MoneyText(converted, base, style = Eq.type.labelMedium, color = c.textTertiary, hidden = hidden)
            }
        }
    }
}

/* ================================================================== */
/* Account detail                                                      */
/* ================================================================== */

class AccountDetailViewModel(c: AppContainer, val accountId: Long) : BaseViewModel(c) {
    fun adjust(actual: Long, note: String) = act("Balance updated") {
        container.repository.adjustBalance(accountId, actual, note, System.currentTimeMillis())
    }
}

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAdd: (TxType) -> Unit,
) {
    val vm = appViewModel(key = "acct-$accountId") { AccountDetailViewModel(it, accountId) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val account = l.accountsById[accountId]
    if (account == null) { LaunchedEffect(Unit) { onBack() }; return }
    val c = Eq.colors
    val hidden = l.settings.hideBalances
    val balance = l.balance(accountId)
    val month = Periods.month(YearMonth.from(l.today))
    val totals = l.totals(month, accountId)
    val trendRange = DateRange(l.today.minusDays(89), l.today)
    val trend = remember(l) { BalanceCalculator.dailyBalances(account, l.data.transactions.map { it.tx }, trendRange, l.zone).map { it.second } }
    val txs = remember(l) { l.data.transactions.filter { it.tx.accountId == accountId || it.tx.toAccountId == accountId } }
    var selected by remember { mutableStateOf<Int?>(null) }
    var adjust by remember { mutableStateOf(false) }
    val tint = Color(account.color)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            EqTopBar(account.name, onBack = onBack, subtitle = account.type.label + " · " + account.currency) {
                CircleIconButton(Icons.Rounded.Edit, "Edit", onEdit)
            }
        }
        item {
            HeroSurface(Modifier.fillMaxWidth(), brush = Brush.linearGradient(listOf(lerp(tint, Color(0xFF060914), 0.35f), Color(0xFF0B1024))), glow = tint) {
                Column(Modifier.padding(22.dp)) {
                    Overline("Current balance", color = Color.White.copy(alpha = 0.7f))
                    MoneyText(balance, account.currency, style = Eq.type.displaySmall, color = Color.White, hidden = hidden, animate = true)
                    Spacer(Modifier.height(12.dp))
                    val sel = selected
                    Text(
                        if (sel != null && sel in trend.indices) "${Fmt.short(trendRange.start.plusDays(sel.toLong()))}: ${Money.format(trend[sel], account.currency)}" else "Last 90 days",
                        style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.7f),
                    )
                    TrendLine(trend, Modifier.fillMaxWidth().height(90.dp), color = Color.White, selected = selected, onSelect = { selected = it })
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("In this month", style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.6f))
                            MoneyText(totals.income, l.base, style = Eq.type.titleSmall, color = Color.White, hidden = hidden)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Out this month", style = Eq.type.labelMedium, color = Color.White.copy(alpha = 0.6f))
                            MoneyText(totals.expense, l.base, style = Eq.type.titleSmall, color = Color.White, hidden = hidden)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Expense", { onAdd(TxType.EXPENSE) }, Modifier.weight(1f), color = c.expense)
                SecondaryButton("Income", { onAdd(TxType.INCOME) }, Modifier.weight(1f), color = c.income)
                SecondaryButton("Transfer", { onAdd(TxType.TRANSFER) }, Modifier.weight(1f), icon = Icons.Rounded.SwapHoriz, color = c.transfer)
            }
        }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SettingRow(Icons.Rounded.Tune, "Adjust balance", "Match the real balance without recording income or spending", onClick = { adjust = true })
            }
        }
        item { SectionHeader("History · ${txs.size}") }
        if (txs.isEmpty()) item { EqCard { Text("No transactions in this account yet.", style = Eq.type.bodyMedium, color = c.textSecondary) } }
        else item {
            EqCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                txs.take(200).forEach { t -> TransactionRow(l.visual(t, accountId), onClick = { onOpenTransaction(t.tx.id) }, hidden = hidden, trailingNote = Fmt.short(l.dateOf(t))) }
            }
        }
    }

    if (adjust) {
        var text by remember { mutableStateOf(Money.editable(balance, account.currency)) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adjust = false },
            containerColor = c.surfaceRaised,
            title = { Text("Adjust balance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the balance you actually have. The difference is recorded as an adjustment, which never counts as income or expense.", style = Eq.type.bodySmall, color = c.textSecondary)
                    EqTextField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } }, "Actual balance", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(account.currency).symbol)
                    EqTextField(note, { note = it }, "Note (optional)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Money.parseToMinor(text, account.currency)
                    if (v != null) vm.adjust(v, note)
                    adjust = false
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { adjust = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
}

/* ================================================================== */
/* Account editor                                                      */
/* ================================================================== */

class AccountEditViewModel(c: AppContainer) : BaseViewModel(c) {
    fun save(a: AccountEntity) = act(if (a.id == 0L) "Account created" else "Account saved", close = true) { container.repository.saveAccount(a) }
    fun delete(a: AccountEntity) = act("Account deleted", close = true) { container.repository.deleteAccount(a) }
    fun archive(a: AccountEntity, archived: Boolean) = act(if (archived) "Account archived" else "Account restored", close = true) {
        container.repository.saveAccount(a.copy(archived = archived))
    }
}

@Composable
fun AccountEditScreen(accountId: Long, onClose: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel(key = "acctEdit-$accountId") { AccountEditViewModel(it) }
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val existing = l.accountsById[accountId]
    var deleted by remember { mutableStateOf(false) }
    VmEffects(vm, onDone = { if (deleted) onDeleted() else onClose() })
    val c = Eq.colors

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: l.base) }
    var opening by rememberSaveable { mutableStateOf(existing?.let { Money.editable(it.openingBalanceMinor, it.currency) } ?: "") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: Defaults.accountColor(AccountType.CASH)) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: Defaults.accountIcon(AccountType.CASH)) }
    var inTotals by rememberSaveable { mutableStateOf(existing?.includeInTotals ?: true) }
    var spendable by rememberSaveable { mutableStateOf(existing?.includeInSafeToSpend ?: true) }
    var currencySheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val txCount = remember(l) { l.data.transactions.count { it.tx.accountId == accountId || it.tx.toAccountId == accountId } }

    Column(Modifier.fillMaxSize()) {
        EqTopBar(if (existing == null) "New account" else "Edit account", onBack = onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Live preview
            EqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.of(icon), Color(color), size = 48.dp, corner = 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.ifBlank { "Account name" }, style = Eq.type.titleMedium, color = if (name.isBlank()) c.textTertiary else c.textPrimary)
                        Text("${type.label} · $currency", style = Eq.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
            EqTextField(name, { name = it.take(40) }, "Name", placeholder = "e.g. GCash, BPI Savings, Wallet")
            Overline("Type")
            SegmentedControl(AccountType.entries.toList(), type, { t ->
                if (existing == null) {
                    if (color == Defaults.accountColor(type)) color = Defaults.accountColor(t)
                    if (icon == Defaults.accountIcon(type)) icon = Defaults.accountIcon(t)
                }
                type = t
            }, label = { it.label })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EqTextField(
                    opening, { opening = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                    if (existing == null) "Starting balance" else "Opening balance",
                    keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol,
                    modifier = Modifier.weight(1f),
                )
                ChoiceChip(currency, false, { if (existing == null || txCount == 0) currencySheet = true })
            }
            if (existing != null && txCount > 0) {
                Text("Currency is locked because this account has transactions. Use \"Adjust balance\" to correct today's balance.", style = Eq.type.bodySmall, color = c.textTertiary)
            }
            Overline("Color")
            SwatchPicker(Defaults.swatches, color, { color = it })
            Overline("Icon")
            IconPicker(com.equinoxtrack.app.ui.components.AppIcons.accountChoices, icon, Color(color), { icon = it })
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SettingRow(Icons.Rounded.Add, "Include in totals", "Counts toward total balance and net worth", trailing = { EqSwitch(inTotals) { inTotals = it } })
                SettingRow(Icons.Rounded.Tune, "Spendable money", "Feeds Safe-to-Spend (turn off for emergency funds)", trailing = { EqSwitch(spendable && inTotals) { spendable = it } })
            }
            if (existing != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(if (existing.archived) "Restore" else "Archive", { vm.archive(existing, !existing.archived) }, Modifier.weight(1f), icon = if (existing.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive)
                    SecondaryButton("Delete", { confirmDelete = true }, Modifier.weight(1f), icon = Icons.Rounded.DeleteOutline, color = c.expense)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryButton("Save account", {
                val openingMinor = if (opening.isBlank()) 0L else Money.parseToMinor(opening, currency) ?: 0L
                val base = existing ?: AccountEntity(name = "", type = type, currency = currency, color = color, icon = icon)
                vm.save(base.copy(name = name, type = type, currency = currency, openingBalanceMinor = openingMinor, color = color, icon = icon, includeInTotals = inTotals, includeInSafeToSpend = spendable && inTotals))
            }, Modifier.fillMaxWidth().then(Modifier.background(Color.Transparent)))
        }
    }
    if (currencySheet) {
        PickerSheet("Currency", CurrencyCatalog.all.map { PickerItem(it.code, "${it.code} · ${it.symbol}", it.name) }, currency, { currency = it }, { currencySheet = false }, searchable = true)
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Delete ${existing.name}?",
            "This permanently removes the account and its $txCount transactions, including transfers to or from it (the other account's balance changes too). Archive instead to keep history.",
            "Delete", { deleted = true; vm.delete(existing) }, { confirmDelete = false },
        )
    }
}

/* ================================================================== */
/* Asset editor with valuation history                                 */
/* ================================================================== */

class AssetEditViewModel(c: AppContainer) : BaseViewModel(c) {
    fun save(a: AssetEntity, initial: Long?) = act(if (a.id == 0L) "Asset added" else "Asset saved", close = a.id == 0L) { container.repository.saveAsset(a, initial) }
    fun revalue(assetId: Long, value: Long, date: LocalDate, note: String) = act("Value updated") { container.repository.addValuation(assetId, value, date, note) }
    fun deleteValuation(id: Long) = act { container.repository.deleteValuation(id) }
    fun delete(id: Long) = act("Asset deleted", close = true) { container.repository.deleteAsset(id) }
}

@Composable
fun AssetEditScreen(assetId: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "asset-$assetId") { AssetEditViewModel(it) }
    VmEffects(vm, onDone = onClose)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val existing = l.data.assets.firstOrNull { it.id == assetId }
    val c = Eq.colors

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: AssetKind.PROPERTY) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: l.base) }
    var value by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: Defaults.swatches[1]) }
    var include by rememberSaveable { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var newValue by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var currencySheet by remember { mutableStateOf(false) }
    val history = l.data.valuations.filter { it.assetId == assetId }

    Column(Modifier.fillMaxSize()) {
        EqTopBar(if (existing == null) "New asset" else existing.name, onBack = onClose) {
            if (existing != null) CircleIconButton(Icons.Rounded.DeleteOutline, "Delete", { confirmDelete = true }, tint = c.expense)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EqTextField(name, { name = it.take(40) }, "Name", placeholder = "e.g. Condo unit, Motorcycle")
            Overline("Kind")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetKind.entries.take(3).forEach { k -> ChoiceChip(k.label, kind == k, { kind = k }, icon = AppIcons.asset(k)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetKind.entries.drop(3).forEach { k -> ChoiceChip(k.label, kind == k, { kind = k }, icon = AppIcons.asset(k)) }
                ChoiceChip(currency, false, { if (existing == null) currencySheet = true })
            }
            if (existing == null) {
                EqTextField(value, { value = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Current value", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol)
            }
            EqTextField(note, { note = it.take(120) }, "Note (optional)")
            Overline("Color")
            SwatchPicker(Defaults.swatches, color, { color = it })
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                SettingRow(AppIcons.asset(kind), "Include in net worth", trailing = { EqSwitch(include) { include = it } })
            }
            PrimaryButton(if (existing == null) "Add asset" else "Save details", {
                val base = existing ?: AssetEntity(name = "", kind = kind, currency = currency, color = color)
                vm.save(base.copy(name = name, kind = kind, currency = currency, color = color, includeInNetWorth = include, note = note), Money.parseToMinor(value, currency) ?: 0L)
            }, Modifier.fillMaxWidth())

            if (existing != null) {
                SectionHeader("Update value")
                EqCard {
                    EqTextField(newValue, { newValue = it.filter { ch -> ch.isDigit() || ch == '.' } }, "New value", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(existing.currency).symbol)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChip(Fmt.full(newDate), false, { pickDate = true })
                        Spacer(Modifier.weight(1f))
                        PrimaryButton("Record", {
                            val v = Money.parseToMinor(newValue, existing.currency)
                            if (v != null) { vm.revalue(existing.id, v, newDate, "Revaluation"); newValue = "" }
                        })
                    }
                }
                SectionHeader("Value history")
                EqCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    history.forEachIndexed { i, v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.full(LocalDate.ofEpochDay(v.epochDay)), style = Eq.type.titleSmall, color = c.textPrimary)
                                if (v.note.isNotBlank()) Text(v.note, style = Eq.type.bodySmall, color = c.textSecondary)
                            }
                            MoneyText(v.valueMinor, existing.currency)
                            if (history.size > 1) CircleIconButton(Icons.Rounded.DeleteOutline, "Remove", { vm.deleteValuation(v.id) }, size = 32.dp, modifier = Modifier.padding(start = 8.dp))
                        }
                        if (i < history.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.stroke))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickDate) EqDatePickerDialog(newDate, { pickDate = false }) { newDate = it }
    if (currencySheet) PickerSheet("Currency", CurrencyCatalog.all.map { PickerItem(it.code, "${it.code} · ${it.symbol}", it.name) }, currency, { currency = it }, { currencySheet = false }, searchable = true)
    if (confirmDelete && existing != null) ConfirmDialog("Delete ${existing.name}?", "Its value history will be removed and net worth recalculated.", "Delete", { vm.delete(existing.id) }, { confirmDelete = false })
}

