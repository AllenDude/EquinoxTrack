package com.equinoxtrack.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.BuildConfig
import com.equinoxtrack.app.data.db.CategoryEntity
import com.equinoxtrack.app.data.io.BackupPasswordException
import com.equinoxtrack.app.data.io.BackupPayload
import com.equinoxtrack.app.data.io.ImportPreview
import com.equinoxtrack.app.domain.AccentPalette
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.CurrencyCatalog
import com.equinoxtrack.app.domain.Defaults
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.ThemeMode
import com.equinoxtrack.app.ui.common.BaseViewModel
import com.equinoxtrack.app.ui.common.Fmt
import com.equinoxtrack.app.ui.common.VmEffects
import com.equinoxtrack.app.ui.common.appViewModel
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.ChoiceChip
import com.equinoxtrack.app.ui.components.CircleIconButton
import com.equinoxtrack.app.ui.components.ConfirmDialog
import com.equinoxtrack.app.ui.components.EqCard
import com.equinoxtrack.app.ui.components.EqSwitch
import com.equinoxtrack.app.ui.components.EqTextField
import com.equinoxtrack.app.ui.components.EqTopBar
import com.equinoxtrack.app.ui.components.HeroSurface
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.IconPicker
import com.equinoxtrack.app.ui.components.Overline
import com.equinoxtrack.app.ui.components.PickerItem
import com.equinoxtrack.app.ui.components.PickerSheet
import com.equinoxtrack.app.ui.components.PrimaryButton
import com.equinoxtrack.app.ui.components.SectionHeader
import com.equinoxtrack.app.ui.components.SegmentedControl
import com.equinoxtrack.app.ui.components.SettingRow
import com.equinoxtrack.app.ui.components.SwatchPicker
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.Eq
import com.equinoxtrack.app.ui.theme.accentPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/* ================================================================== */
/* Settings                                                            */
/* ================================================================== */

class SettingsViewModel(c: AppContainer) : BaseViewModel(c) {
    val settings = container.settingsState
    fun theme(m: ThemeMode) { viewModelScope.launch { container.settings.setThemeMode(m) } }
    fun accent(a: AccentPalette) { viewModelScope.launch { container.settings.setAccent(a) } }
    fun currency(code: String) { viewModelScope.launch { container.settings.setBaseCurrency(code) } }
    fun biometric(on: Boolean) { viewModelScope.launch { container.settings.setBiometricLock(on) } }
    fun reminders(on: Boolean) { viewModelScope.launch { container.settings.setBillReminders(on) } }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onData: () -> Unit, onRates: () -> Unit, onSafe: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    VmEffects(vm)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val c = Eq.colors
    val context = LocalContext.current
    var currencySheet by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.reminders(granted) }
    val canBiometric = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EqTopBar("Settings", onBack = onBack) }
        item { SectionHeader("Appearance") }
        item {
            EqCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.DarkMode, c.accent, size = 38.dp, corner = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Theme", style = Eq.type.titleSmall, color = c.textPrimary)
                }
                Spacer(Modifier.height(12.dp))
                SegmentedControl(ThemeMode.entries.toList(), s.themeMode, { vm.theme(it) }, label = { it.label })
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette, c.accent, size = 38.dp, corner = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Accent", style = Eq.type.titleSmall, color = c.textPrimary)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.entries.forEach { a ->
                        val (p1, p2) = accentPreview(a)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable({ vm.accent(a) }, RoundedCornerShape(14.dp)).padding(4.dp)) {
                            Box(
                                Modifier.size(46.dp).background(Brush.linearGradient(listOf(p1, p2)), CircleShape)
                                    .border(if (s.accent == a) 3.dp else 0.dp, if (s.accent == a) c.textPrimary else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (s.accent == a) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                            Spacer(Modifier.height(4.dp))
                            Text(a.label, style = Eq.type.labelMedium, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        item { SectionHeader("Money") }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                val info = CurrencyCatalog.info(s.baseCurrency)
                SettingRow(Icons.Rounded.Public, "Main currency", "${info.code} · ${info.name}", onClick = { currencySheet = true })
                SettingRow(Icons.Rounded.Sync, "Exchange rates", "For accounts in other currencies", onClick = onRates)
                SettingRow(Icons.Rounded.Category, "Categories", "Add, edit and archive categories", onClick = onCategories)
                SettingRow(Icons.Rounded.Event, "Safe-to-Spend", "Period start, cushion, goal protection", onClick = onSafe)
            }
        }
        item { SectionHeader("Security & privacy") }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                SettingRow(
                    Icons.Rounded.Fingerprint, "App lock",
                    if (canBiometric) "Require fingerprint, face or screen lock to open" else "Set up a screen lock on this device first",
                    trailing = { EqSwitch(s.biometricLock && canBiometric) { if (canBiometric) vm.biometric(it) } },
                )
                SettingRow(
                    Icons.Rounded.Notifications, "Bill reminders", "Notify before bills are due",
                    trailing = {
                        EqSwitch(s.billReminders) { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.reminders(on)
                        }
                    },
                )
                SettingRow(Icons.Rounded.Shield, "Private by design", "Everything stays on this device. No account, ads or tracking.")
            }
        }
        item { SectionHeader("Data") }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Backup, "Backup, restore & export", "CSV import/export, encrypted backups, reports", onClick = onData)
            }
        }
        item {
            EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                SettingRow(Icons.Rounded.Info, "EquinoxTrack ${BuildConfig.VERSION_NAME}", "Personal finance, balanced. Works fully offline.")
            }
        }
    }
    if (currencySheet) {
        PickerSheet(
            "Main currency", CurrencyCatalog.all.map { PickerItem(it.code, "${it.code} · ${it.symbol}", it.name) }, s.baseCurrency,
            { vm.currency(it) }, { currencySheet = false }, searchable = true,
        )
    }
}

/* ================================================================== */
/* Categories                                                          */
/* ================================================================== */

class CategoriesViewModel(c: AppContainer) : BaseViewModel(c) {
    fun save(cat: CategoryEntity) = act(if (cat.id == 0L) "Category added" else "Category saved") { container.repository.saveCategory(cat) }
    fun remove(cat: CategoryEntity) {
        viewModelScope.launch {
            val deleted = container.repository.removeCategory(cat)
            message(if (deleted) "Category deleted" else "Category archived (it has history)")
        }
    }
    fun restore(cat: CategoryEntity) = act("Category restored") { container.repository.saveCategory(cat.copy(archived = false)) }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val l = ledger ?: return
    val c = Eq.colors
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var confirm by remember { mutableStateOf<CategoryEntity?>(null) }
    val list = l.data.categories.filter { it.kind == kind }.sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.name.lowercase() }))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            EqTopBar("Categories", onBack = onBack) {
                CircleIconButton(Icons.Rounded.Add, "Add", { editing = CategoryEntity(name = "", kind = kind, color = Defaults.swatches[0], icon = "tag") })
            }
        }
        item { SegmentedControl(listOf(CategoryKind.EXPENSE, CategoryKind.INCOME), kind, { kind = it }, label = { if (it == CategoryKind.EXPENSE) "Expense" else "Income" }) }
        item {
            EqCard(padding = PaddingValues(8.dp)) {
                list.forEach { cat ->
                    Row(Modifier.fillMaxWidth().pressable({ editing = cat }, RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AppIcons.of(cat.icon), Color(cat.color), size = 38.dp, corner = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(cat.name, style = Eq.type.titleSmall, color = if (cat.archived) c.textTertiary else c.textPrimary, modifier = Modifier.weight(1f))
                        if (cat.archived) CircleIconButton(Icons.Rounded.Unarchive, "Restore", { vm.restore(cat) }, size = 34.dp)
                        else CircleIconButton(Icons.Rounded.Archive, "Remove", { confirm = cat }, size = 34.dp)
                    }
                }
            }
        }
    }
    editing?.let { cat ->
        var name by remember(cat) { mutableStateOf(cat.name) }
        var color by remember(cat) { mutableStateOf(cat.color) }
        var icon by remember(cat) { mutableStateOf(cat.icon) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = c.surfaceRaised,
            title = { Text(if (cat.id == 0L) "New category" else "Edit category") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EqTextField(name, { name = it.take(30) }, "Name")
                    SwatchPicker(Defaults.swatches, color, { color = it })
                    IconPicker(AppIcons.categoryChoices, icon, Color(color), { icon = it })
                }
            },
            confirmButton = { TextButton(onClick = { vm.save(cat.copy(name = name, color = color, icon = icon)); editing = null }) { Text("Save", color = c.accent) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
    confirm?.let { cat ->
        ConfirmDialog("Remove ${cat.name}?", "Unused categories are deleted. Categories with transactions are archived so your history and reports stay intact.", "Remove", { vm.remove(cat) }, { confirm = null })
    }
}

/* ================================================================== */
/* Data: import / export / backup / reports                            */
/* ================================================================== */

class DataViewModel(c: AppContainer) : BaseViewModel(c) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview
    private val _pendingRestore = MutableStateFlow<BackupPayload?>(null)
    val pendingRestore: StateFlow<BackupPayload?> = _pendingRestore
    private val _needsPassword = MutableStateFlow<android.net.Uri?>(null)
    val needsPassword: StateFlow<android.net.Uri?> = _needsPassword

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try { block() } catch (e: Exception) { message(e.message ?: "Something went wrong") } finally { _busy.value = false }
        }
    }

    private suspend fun ledgerNow() = ledger.value ?: container.loadLedger()

    fun exportCsv(uri: android.net.Uri) = work { val n = container.dataTools.exportCsv(uri, ledgerNow()); message("Exported $n transactions") }
    fun previewImport(uri: android.net.Uri) = work { _preview.value = container.dataTools.previewImport(uri, ledgerNow()) }
    fun commitImport(skipDuplicates: Boolean) = work {
        val p = _preview.value ?: return@work
        val n = container.dataTools.commitImport(p, ledgerNow(), skipDuplicates)
        _preview.value = null
        message("Imported $n transactions")
    }
    fun cancelImport() { _preview.value = null }
    fun backup(uri: android.net.Uri, password: String?) = work {
        container.dataTools.writeBackup(uri, password)
        message(if (password.isNullOrEmpty()) "Backup saved" else "Encrypted backup saved")
    }
    fun openBackup(uri: android.net.Uri, password: String?) = work {
        try {
            _pendingRestore.value = container.dataTools.readBackup(uri, password)
            _needsPassword.value = null
        } catch (e: BackupPasswordException) {
            _needsPassword.value = uri
        }
    }
    fun cancelRestore() { _pendingRestore.value = null; _needsPassword.value = null }
    fun confirmRestore() = work {
        val p = _pendingRestore.value ?: return@work
        container.dataTools.restore(p)
        _pendingRestore.value = null
        message("Restored ${p.transactions.size} transactions")
    }
    fun summaryCsv(uri: android.net.Uri, year: Int) = work { container.dataTools.exportSummaryCsv(uri, ledgerNow(), year); message("Summary exported") }
    fun monthlyPdf(uri: android.net.Uri, month: YearMonth) = work { container.dataTools.exportMonthlyPdf(uri, ledgerNow(), month); message("Report exported") }
}

@Composable
fun DataScreen(onBack: () -> Unit) {
    val vm = appViewModel { DataViewModel(it) }
    VmEffects(vm)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val needsPassword by vm.needsPassword.collectAsStateWithLifecycle()
    val c = Eq.colors
    val today = LocalDate.now()
    var reportMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var backupPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var monthSheet by remember { mutableStateOf(false) }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::previewImport) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.backup(it, backupPassword.ifBlank { null }) } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.openBackup(it, null) } }
    val summary = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.summaryCsv(it, reportMonth.year) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { vm.monthlyPdf(it, reportMonth) } }

    Column(Modifier.fillMaxSize()) {
        EqTopBar("Data & backup", onBack = onBack) { if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("Transactions (CSV)") }
            item {
                EqCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    SettingRow(Icons.Rounded.FileUpload, "Export transactions", "All transactions as CSV (Excel / Sheets friendly)", onClick = { exportCsv.launch("equinoxtrack-transactions-$today.csv") })
                    SettingRow(Icons.Rounded.FileDownload, "Import transactions", "From CSV · duplicates are detected before importing", onClick = { importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) })
                }
            }
            item {
                Text(
                    "CSV columns: Date (YYYY-MM-DD), Time, Type (Expense/Income/Transfer/Adjustment), Account, To Account, Category, Splits (Food=120.50|Transport=30), Amount, Currency, To Amount, Note.",
                    style = Eq.type.bodySmall, color = c.textTertiary,
                )
            }
            item { SectionHeader("Backup & restore") }
            item {
                EqCard {
                    Text("Backups contain everything: accounts, transactions, bills, budgets, goals, assets and settings. Store them wherever you like — this app never uploads anything.", style = Eq.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    EqTextField(backupPassword, { backupPassword = it }, "Password (optional, encrypts the backup)", keyboardType = KeyboardType.Password, password = true)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Create backup", { backup.launch("equinoxtrack-$today.eqxbackup") }, Modifier.weight(1f), icon = Icons.Rounded.Backup)
                        com.equinoxtrack.app.ui.components.SecondaryButton("Restore", { restore.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = Icons.Rounded.Restore)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Encrypted with AES-256-GCM. Without the password the backup cannot be restored.", style = Eq.type.labelSmall, color = c.textTertiary)
                }
            }
            item { SectionHeader("Reports") }
            item {
                EqCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Report period", style = Eq.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                        ChoiceChip(reportMonth.atDay(1).let { Fmt.month(it) }, true, { monthSheet = true })
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingRow(Icons.Rounded.PictureAsPdf, "Monthly report (PDF)", "Summary, categories, budgets, goals and every transaction", onClick = { pdf.launch("equinoxtrack-report-$reportMonth.pdf") })
                    SettingRow(Icons.Rounded.TableChart, "Year summary (CSV)", "Monthly totals and category breakdown for ${reportMonth.year}", onClick = { summary.launch("equinoxtrack-summary-${reportMonth.year}.csv") })
                }
            }
        }
    }

    if (monthSheet) {
        val months = (0 until 24).map { YearMonth.from(today).minusMonths(it.toLong()) }
        PickerSheet("Report month", months.map { PickerItem(it, Fmt.month(it.atDay(1))) }, reportMonth, { reportMonth = it }, { monthSheet = false })
    }
    preview?.let { p ->
        var skip by remember(p) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            containerColor = c.surfaceRaised,
            title = { Text("Import ${p.rows.size} rows?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• ${p.importable} new transactions", style = Eq.type.bodyMedium, color = c.textPrimary)
                    Text("• ${p.duplicateCount} look like duplicates", style = Eq.type.bodyMedium, color = if (p.duplicateCount > 0) c.warning else c.textSecondary)
                    if (p.newAccounts.isNotEmpty()) Text("• New accounts: ${p.newAccounts.joinToString()}", style = Eq.type.bodyMedium, color = c.textSecondary)
                    if (p.newCategories.isNotEmpty()) Text("• New categories: ${p.newCategories.joinToString()}", style = Eq.type.bodyMedium, color = c.textSecondary)
                    if (p.errors.isNotEmpty()) {
                        Text("${p.errors.size} rows skipped:", style = Eq.type.bodyMedium, color = c.expense)
                        p.errors.take(6).forEach { Text(it, style = Eq.type.bodySmall, color = c.textTertiary) }
                    }
                    if (p.duplicateCount > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Skip duplicates", style = Eq.type.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                        EqSwitch(skip) { skip = it }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.commitImport(skip) }, enabled = p.rows.isNotEmpty()) { Text("Import", color = c.accent) } },
            dismissButton = { TextButton(onClick = { vm.cancelImport() }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
    needsPassword?.let { uri ->
        AlertDialog(
            onDismissRequest = { vm.cancelRestore() },
            containerColor = c.surfaceRaised,
            title = { Text("Encrypted backup") },
            text = { EqTextField(restorePassword, { restorePassword = it }, "Backup password", keyboardType = KeyboardType.Password, password = true) },
            confirmButton = { TextButton(onClick = { vm.openBackup(uri, restorePassword) }) { Text("Unlock", color = c.accent) } },
            dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text("Cancel", color = c.textSecondary) } },
        )
    }
    pending?.let { p ->
        val date = Instant.ofEpochMilli(p.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        ConfirmDialog(
            "Replace all data?",
            "This backup from ${Fmt.full(date)} has ${p.accounts.size} accounts and ${p.transactions.size} transactions. Everything currently in the app will be replaced. This cannot be undone.",
            "Restore", { vm.confirmRestore() }, { vm.cancelRestore() },
        )
    }
}

/* ================================================================== */
/* Exchange rates                                                      */
/* ================================================================== */

class RatesViewModel(c: AppContainer) : BaseViewModel(c) {
    val updatedAt = container.settings.ratesUpdatedAt
    fun setRate(code: String, units: BigDecimal, base: String) = act("Rate saved") { container.repository.setManualRate(code, units, base) }
    fun fetch() = act("Rates updated") {
        val rates = container.dataTools.fetchOnlineRates()
        container.repository.replaceRates(rates)
        container.settings.setRatesUpdated(System.currentTimeMillis())
    }
}

@Composable
fun RatesScreen(onBack: () -> Unit) {
    val vm = appViewModel { RatesViewModel(it) }
    VmEffects(vm)
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val updated by vm.updatedAt.collectAsStateWithLifecycle(0L)
    val l = ledger ?: return
    val c = Eq.colors
    val used = (l.data.accounts.map { it.currency } + l.data.assets.map { it.currency } + l.data.goals.map { it.currency } + l.data.bills.map { it.currency })
        .filter { it != l.base }.distinct().sorted()
    var extra by remember { mutableStateOf(listOf<String>()) }
    var addSheet by remember { mutableStateOf(false) }
    val shown = (used + extra).distinct()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EqTopBar("Exchange rates", onBack = onBack, subtitle = "Everything totals in ${l.base}") { CircleIconButton(Icons.Rounded.Add, "Add currency", { addSheet = true }) } }
        item {
            EqCard {
                Text("Rates are only used to show totals in ${l.base}. Each account keeps its own currency and real balance.", style = Eq.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Update from the internet", style = Eq.type.titleSmall, color = c.textPrimary)
                        Text(if (updated > 0) "Last updated ${Fmt.full(Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault()).toLocalDate())}" else "Optional · uses open.er-api.com", style = Eq.type.bodySmall, color = c.textTertiary)
                    }
                    ChoiceChip("Update", false, { vm.fetch() }, icon = Icons.Rounded.Sync)
                }
            }
        }
        if (shown.isEmpty()) item {
            EqCard { Text("All your accounts use ${l.base}. Add a currency only if you hold money in another one.", style = Eq.type.bodyMedium, color = c.textSecondary) }
        }
        shown.forEach { code ->
            item(key = code) {
                val current = l.converter.rate(code, l.base)
                var text by remember(current) { mutableStateOf(current?.stripTrailingZeros()?.toPlainString() ?: "") }
                EqCard(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("1 $code =", style = Eq.type.titleSmall, color = c.textPrimary)
                            Text(CurrencyCatalog.info(code).name + if (current == null) " · rate needed" else "", style = Eq.type.bodySmall, color = if (current == null) c.warning else c.textTertiary)
                        }
                        EqTextField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' } }, l.base, keyboardType = KeyboardType.Decimal, modifier = Modifier.width(140.dp))
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(Icons.Rounded.Check, "Save", { text.toBigDecimalOrNull()?.let { vm.setRate(code, it, l.base) } }, size = 38.dp)
                    }
                }
            }
        }
    }
    if (addSheet) PickerSheet("Add currency", CurrencyCatalog.all.filter { it.code != l.base }.map { PickerItem(it.code, "${it.code} · ${it.symbol}", it.name) }, null, { extra = extra + it }, { addSheet = false }, searchable = true)
}

/* ================================================================== */
/* Onboarding                                                          */
/* ================================================================== */

class OnboardingViewModel(c: AppContainer) : BaseViewModel(c) {
    fun finish(currency: String, accountName: String, type: AccountType, opening: Long) {
        viewModelScope.launch {
            container.settings.setBaseCurrency(currency)
            container.repository.seedCategoriesIfEmpty()
            if (container.repository.loadData().accounts.isEmpty()) {
                container.repository.createStarterAccount(accountName.ifBlank { "Cash" }, type, currency, opening)
            }
            container.settings.setOnboardingDone()
        }
    }
}

@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    val c = Eq.colors
    var currency by remember { mutableStateOf("PHP") }
    var name by remember { mutableStateOf("Cash") }
    var type by remember { mutableStateOf(AccountType.CASH) }
    var opening by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HeroSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(26.dp)) {
                Text("EquinoxTrack", style = Eq.type.displaySmall, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Your money, in balance. Private, offline and beautifully clear.", style = Eq.type.bodyLarge, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(18.dp))
                listOf(
                    "Safe-to-Spend tells you what's truly free today",
                    "Bills, budgets and goals planned in one place",
                    "No sign-up, no bank links, no tracking",
                ).forEach {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = c.income, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = Eq.type.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        Overline("Main currency")
        EqCard(onClick = { sheet = true }, padding = PaddingValues(14.dp)) {
            val info = CurrencyCatalog.info(currency)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.symbol, style = Eq.type.headlineSmall, color = c.accent, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                Column(Modifier.weight(1f)) {
                    Text(info.code, style = Eq.type.titleSmall, color = c.textPrimary)
                    Text(info.name, style = Eq.type.bodySmall, color = c.textSecondary)
                }
                Text("Change", style = Eq.type.labelLarge, color = c.accent)
            }
        }
        Overline("Your first account")
        SegmentedControl(AccountType.entries.toList(), type, { t ->
            if (name == "Cash" || name == "Bank" || name == "E-wallet" || name == "Custom") name = t.label
            type = t
        }, label = { it.label })
        EqTextField(name, { name = it.take(40) }, "Account name")
        EqTextField(opening, { opening = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Current balance", keyboardType = KeyboardType.Decimal, prefix = CurrencyCatalog.info(currency).symbol)
        PrimaryButton("Start tracking", { vm.finish(currency, name, type, Money.parseToMinor(opening, currency) ?: 0L) }, Modifier.fillMaxWidth())
        Text("You can add more accounts, change the currency and import data later.", style = Eq.type.bodySmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    if (sheet) PickerSheet("Main currency", CurrencyCatalog.all.map { PickerItem(it.code, "${it.code} · ${it.symbol}", it.name) }, currency, { currency = it }, { sheet = false }, searchable = true)
}

/* ================================================================== */
/* Lock screen                                                         */
/* ================================================================== */

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = Eq.colors
    Column(
        Modifier.fillMaxSize().background(c.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroSurface(Modifier.size(120.dp), shape = CircleShape) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(24.dp))
        Text("EquinoxTrack is locked", style = Eq.type.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Unlock to see your finances.", style = Eq.type.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Unlock", onUnlock, icon = Icons.Rounded.Fingerprint)
    }
}

