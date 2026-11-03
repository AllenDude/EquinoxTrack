package com.equinoxtrack.app.data.io

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.data.db.AssetValuationEntity
import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.BillPaymentEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import com.equinoxtrack.app.data.db.CategoryEntity
import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.data.db.GoalEntryEntity
import com.equinoxtrack.app.data.db.TransactionEntity
import com.equinoxtrack.app.data.db.TransactionLineEntity
import com.equinoxtrack.app.data.db.TransactionWithLines
import com.equinoxtrack.app.data.repo.FinanceRepository
import com.equinoxtrack.app.data.repo.LineDraft
import com.equinoxtrack.app.data.repo.TransactionDraft
import com.equinoxtrack.app.data.settings.SettingsRepository
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.BackupCrypto
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.Csv
import com.equinoxtrack.app.domain.CsvTransaction
import com.equinoxtrack.app.domain.Defaults
import com.equinoxtrack.app.domain.DuplicateDetector
import com.equinoxtrack.app.domain.FinanceData
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.TransactionCsv
import com.equinoxtrack.app.domain.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Serializable
data class BackupPayload(
    val format: String = "equinoxtrack-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val lines: List<TransactionLineEntity>,
    val bills: List<BillEntity>,
    val billPayments: List<BillPaymentEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val goalEntries: List<GoalEntryEntity>,
    val assets: List<AssetEntity>,
    val valuations: List<AssetValuationEntity>,
    val rates: List<ExchangeRateEntity>,
    val settings: Map<String, String> = emptyMap(),
)

data class ImportPreview(
    val rows: List<CsvTransaction>,
    val duplicates: List<Boolean>,
    val errors: List<String>,
    val newAccounts: List<String>,
    val newCategories: List<String>,
) {
    val duplicateCount get() = duplicates.count { it }
    val importable get() = rows.size - duplicateCount
}

class BackupPasswordException : Exception("This backup is encrypted. Enter its password.")

/** File-based import/export. All file access goes through the Storage Access Framework URIs the user picked. */
class DataTools(
    private val context: Context,
    private val repository: FinanceRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val resolver get() = context.contentResolver

    private suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot open the selected file")
    }

    private suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read the selected file")
    }

    /* ---------------- CSV ---------------- */

    private fun toCsv(ledger: Ledger, t: TransactionWithLines): CsvTransaction {
        val tx = t.tx
        val dt = Periods.localDateTime(tx.occurredAt, ledger.zone)
        val currency = ledger.currencyOf(tx.accountId)
        val split = t.lines.size > 1
        return CsvTransaction(
            date = dt.toLocalDate(),
            time = dt.toLocalTime().withSecond(0).withNano(0),
            type = tx.type,
            account = ledger.accountsById[tx.accountId]?.name ?: "",
            toAccount = tx.toAccountId?.let { ledger.accountsById[it]?.name } ?: "",
            category = when {
                split -> "Split"
                t.lines.size == 1 -> t.lines[0].categoryId?.let { ledger.categoriesById[it]?.name } ?: ""
                else -> ""
            },
            splits = if (split) t.lines.map { ledger.categoryName(it.categoryId) to Money.toDecimal(it.amountMinor, currency) } else emptyList(),
            amount = Money.toDecimal(tx.amountMinor, currency),
            currency = currency,
            toAmount = if (tx.type == TxType.TRANSFER) Money.toDecimal(tx.toAmountMinor ?: tx.amountMinor, tx.toAccountId?.let { ledger.currencyOf(it) } ?: currency) else null,
            note = tx.note,
        )
    }

    suspend fun exportCsv(uri: Uri, ledger: Ledger, transactions: List<TransactionWithLines> = ledger.data.transactions): Int {
        val rows = withContext(Dispatchers.Default) { transactions.sortedBy { it.tx.occurredAt }.map { toCsv(ledger, it) } }
        writeBytes(uri, ("﻿" + TransactionCsv.write(rows)).toByteArray(Charsets.UTF_8))
        return rows.size
    }

    private fun existingFingerprints(ledger: Ledger): Set<String> = ledger.data.transactions.map { t ->
        DuplicateDetector.fingerprint(ledger.dateOf(t), t.tx.type, ledger.accountsById[t.tx.accountId]?.name ?: "", t.tx.amountMinor, t.tx.note)
    }.toHashSet()

    private fun currencyFor(ledger: Ledger, accountName: String, fallback: String): String =
        ledger.data.accounts.firstOrNull { it.name.trim().equals(accountName.trim(), ignoreCase = true) }?.currency ?: fallback

    suspend fun previewImport(uri: Uri, ledger: Ledger): ImportPreview {
        val text = readBytes(uri).toString(Charsets.UTF_8)
        return withContext(Dispatchers.Default) {
            val parsed = TransactionCsv.parse(text, ledger.base)
            val fingerprints = parsed.rows.map { r ->
                val cur = currencyFor(ledger, r.account, r.currency)
                DuplicateDetector.fingerprint(r.date, r.type, r.account, Money.toMinor(r.amount, cur), r.note)
            }
            val dupes = DuplicateDetector.flag(existingFingerprints(ledger), fingerprints)
            val knownAccounts = ledger.data.accounts.map { it.name.trim().lowercase() }.toSet()
            val newAccounts = parsed.rows.flatMap { listOf(it.account, it.toAccount) }.filter { it.isNotBlank() }
                .distinctBy { it.trim().lowercase() }.filter { it.trim().lowercase() !in knownAccounts }
            val knownCategories = ledger.data.categories.map { "${it.kind}:${it.name.trim().lowercase()}" }.toSet()
            val newCategories = parsed.rows.flatMap { r ->
                val kind = if (r.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val names = if (r.splits.isNotEmpty()) r.splits.map { it.first } else listOf(r.category)
                if (r.type == TxType.INCOME || r.type == TxType.EXPENSE) names.filter { it.isNotBlank() }.map { kind to it } else emptyList()
            }.distinctBy { "${it.first}:${it.second.trim().lowercase()}" }
                .filter { "${it.first}:${it.second.trim().lowercase()}" !in knownCategories }
                .map { it.second }
            ImportPreview(parsed.rows, dupes, parsed.errors, newAccounts, newCategories)
        }
    }

    suspend fun commitImport(preview: ImportPreview, ledger: Ledger, skipDuplicates: Boolean): Int {
        val rows = preview.rows.filterIndexed { i, _ -> !skipDuplicates || !preview.duplicates[i] }
        val zone = ledger.zone
        val accountCurrency = HashMap<String, String>()
        ledger.data.accounts.forEach { accountCurrency[it.name.trim().lowercase()] = it.currency }
        val newAccounts = rows.flatMap { listOf(it.account to it.currency, it.toAccount to it.currency) }
            .filter { it.first.isNotBlank() && it.first.trim().lowercase() !in accountCurrency }
            .distinctBy { it.first.trim().lowercase() }
            .map { (name, cur) ->
                accountCurrency[name.trim().lowercase()] = cur
                AccountEntity(name = name.trim(), type = AccountType.CUSTOM, currency = cur, color = Defaults.accountColor(AccountType.CUSTOM), icon = Defaults.accountIcon(AccountType.CUSTOM))
            }
        val knownCategories = ledger.data.categories.map { "${it.kind}:${it.name.trim().lowercase()}" }.toMutableSet()
        val newCategories = ArrayList<CategoryEntity>()
        rows.forEach { r ->
            if (r.type != TxType.INCOME && r.type != TxType.EXPENSE) return@forEach
            val kind = if (r.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val names = if (r.splits.isNotEmpty()) r.splits.map { it.first } else listOf(r.category)
            names.filter { it.isNotBlank() }.forEach { n ->
                val key = "$kind:${n.trim().lowercase()}"
                if (knownCategories.add(key)) {
                    val color = Defaults.swatches[newCategories.size % Defaults.swatches.size]
                    newCategories += CategoryEntity(name = n.trim(), kind = kind, color = color, icon = "tag")
                }
            }
        }
        return repository.importBatch(newAccounts, newCategories) { accountIds, categoryIds ->
            rows.mapNotNull { r ->
                val accId = accountIds[r.account.trim().lowercase()] ?: return@mapNotNull null
                val cur = accountCurrency[r.account.trim().lowercase()] ?: r.currency
                val amount = Money.toMinor(r.amount, cur)
                val at = LocalDateTime.of(r.date, r.time).atZone(zone).toInstant().toEpochMilli()
                when (r.type) {
                    TxType.TRANSFER -> {
                        val toId = accountIds[r.toAccount.trim().lowercase()] ?: return@mapNotNull null
                        val toCur = accountCurrency[r.toAccount.trim().lowercase()] ?: cur
                        val toAmount = r.toAmount?.let { Money.toMinor(it, toCur) }
                            ?: ledger.converter.convert(amount, cur, toCur) ?: amount
                        TransactionDraft(type = TxType.TRANSFER, accountId = accId, toAccountId = toId, amountMinor = amount, toAmountMinor = toAmount, note = r.note, occurredAt = at)
                    }
                    TxType.ADJUSTMENT -> TransactionDraft(type = TxType.ADJUSTMENT, accountId = accId, amountMinor = amount, note = r.note, occurredAt = at)
                    else -> {
                        val kind = if (r.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                        fun catId(name: String) = if (name.isBlank()) null else categoryIds["$kind:${name.trim().lowercase()}"]
                        var lines = r.splits.map { LineDraft(catId(it.first), Money.toMinor(it.second, cur)) }
                        if (lines.isEmpty() || lines.sumOf { it.amountMinor } != amount) lines = listOf(LineDraft(catId(r.category), amount))
                        TransactionDraft(type = r.type, accountId = accId, amountMinor = amount, note = r.note, occurredAt = at, lines = lines)
                    }
                }
            }
        }
    }

    /* ---------------- Backup ---------------- */

    suspend fun writeBackup(uri: Uri, password: String?) {
        val data = repository.loadData()
        val lines = repository.allLines()
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            accounts = data.accounts,
            categories = data.categories,
            transactions = data.transactions.map { it.tx },
            lines = lines,
            bills = data.bills,
            billPayments = data.billPayments,
            budgets = data.budgets,
            goals = data.goals,
            goalEntries = data.goalEntries,
            assets = data.assets,
            valuations = data.valuations,
            rates = data.rates,
            settings = settings.toBackupMap(settings.current()),
        )
        val bytes = withContext(Dispatchers.Default) {
            val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
            if (password.isNullOrEmpty()) plain else BackupCrypto.encrypt(plain, password)
        }
        writeBytes(uri, bytes)
    }

    suspend fun isEncryptedBackup(uri: Uri): Boolean = BackupCrypto.isEncrypted(readBytes(uri))

    /** Validates and decodes a backup without touching the database. */
    suspend fun readBackup(uri: Uri, password: String?): BackupPayload {
        val raw = readBytes(uri)
        return withContext(Dispatchers.Default) {
            val plain = if (BackupCrypto.isEncrypted(raw)) {
                if (password.isNullOrEmpty()) throw BackupPasswordException()
                runCatching { BackupCrypto.decrypt(raw, password) }.getOrElse { throw IllegalArgumentException("Wrong password or damaged backup file") }
            } else raw
            val payload = runCatching { json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8)) }
                .getOrElse { throw IllegalArgumentException("This file is not an EquinoxTrack backup") }
            require(payload.format == "equinoxtrack-backup") { "This file is not an EquinoxTrack backup" }
            payload
        }
    }

    suspend fun restore(payload: BackupPayload) {
        val linesByTx = payload.lines.groupBy { it.transactionId }
        val data = FinanceData(
            accounts = payload.accounts,
            categories = payload.categories,
            transactions = payload.transactions.map { TransactionWithLines(it, linesByTx[it.id].orEmpty()) },
            bills = payload.bills,
            billPayments = payload.billPayments,
            budgets = payload.budgets,
            goals = payload.goals,
            goalEntries = payload.goalEntries,
            assets = payload.assets,
            valuations = payload.valuations,
            rates = payload.rates,
        )
        repository.replaceAll(data, payload.lines)
        settings.restore(payload.settings)
    }

    /* ---------------- Reports ---------------- */

    /** Year summary: monthly totals and category totals, spreadsheet friendly. */
    suspend fun exportSummaryCsv(uri: Uri, ledger: Ledger, year: Int) {
        val text = withContext(Dispatchers.Default) {
            buildString {
                append(Csv.row(listOf("EquinoxTrack summary", year.toString(), "Currency: ${ledger.base}"))).append("\n\n")
                append(Csv.row(listOf("Month", "Income", "Expenses", "Net", "Savings rate"))).append("\n")
                (1..12).forEach { m ->
                    val t = ledger.totals(Periods.month(YearMonth.of(year, m)))
                    append(Csv.row(listOf(YearMonth.of(year, m).toString(), Money.plain(t.income, ledger.base), Money.plain(t.expense, ledger.base), Money.plain(t.net, ledger.base), "%.1f%%".format(t.savingsRate * 100)))).append("\n")
                }
                val yr = Periods.year(year)
                val total = ledger.totals(yr)
                append(Csv.row(listOf("Total", Money.plain(total.income, ledger.base), Money.plain(total.expense, ledger.base), Money.plain(total.net, ledger.base), "%.1f%%".format(total.savingsRate * 100)))).append("\n\n")
                append(Csv.row(listOf("Expense category", "Amount", "Share"))).append("\n")
                ledger.byCategory(yr, CategoryKind.EXPENSE).forEach { c ->
                    val share = if (total.expense > 0) c.amount * 100.0 / total.expense else 0.0
                    append(Csv.row(listOf(ledger.categoryName(c.categoryId), Money.plain(c.amount, ledger.base), "%.1f%%".format(share)))).append("\n")
                }
                append("\n").append(Csv.row(listOf("Income category", "Amount"))).append("\n")
                ledger.byCategory(yr, CategoryKind.INCOME).forEach { c ->
                    append(Csv.row(listOf(ledger.categoryName(c.categoryId), Money.plain(c.amount, ledger.base)))).append("\n")
                }
            }
        }
        writeBytes(uri, ("﻿" + text).toByteArray(Charsets.UTF_8))
    }

    /** Monthly PDF report drawn with the platform PdfDocument API (no external libraries). */
    suspend fun exportMonthlyPdf(uri: Uri, ledger: Ledger, month: YearMonth) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 48f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.rgb(13, 18, 48) }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.rgb(124, 92, 255) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(40, 44, 70) }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(120, 126, 150) }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(124, 92, 255) }
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 234, 245) }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
            page!!.canvas.drawText("EquinoxTrack · ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", margin, pageH - 24f, muted)
            page!!.canvas.drawText("Page $pageNo", pageW - margin, pageH - 24f, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
        }
        fun ensure(space: Float) { if (page == null || y + space > pageH - 60f) newPage() }
        fun row(label: String, value: String, paint: Paint = body) {
            ensure(18f)
            page!!.canvas.drawText(label, margin, y, paint)
            page!!.canvas.drawText(value, pageW - margin, y, right)
            y += 17f
        }
        fun section(name: String) { ensure(40f); y += 14f; page!!.canvas.drawText(name.uppercase(), margin, y, h2); y += 20f }
        fun fmt(v: Long) = Money.format(v, ledger.base)

        newPage()
        val c = page!!.canvas
        c.drawText("Monthly financial report", margin, y + 10f, title)
        y += 32f
        c.drawText("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year} · Generated ${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)} · Amounts in ${ledger.base}", margin, y, muted)
        y += 16f

        val range = Periods.month(month)
        val totals = ledger.totals(range)
        section("Summary")
        row("Income", fmt(totals.income))
        row("Expenses", fmt(totals.expense))
        row("Net cash flow", fmt(totals.net))
        row("Savings rate", "%.1f%%".format(totals.savingsRate * 100))
        row("Total balance (today)", fmt(ledger.totalBalance))
        row("Net worth (today)", fmt(ledger.netWorth.total))

        section("Spending by category")
        val cats = ledger.byCategory(range, CategoryKind.EXPENSE)
        if (cats.isEmpty()) row("No expenses recorded", "", muted)
        cats.forEach { ca ->
            ensure(30f)
            val share = if (totals.expense > 0) ca.amount.toFloat() / totals.expense else 0f
            page!!.canvas.drawText(ledger.categoryName(ca.categoryId), margin, y, body)
            page!!.canvas.drawText("${fmt(ca.amount)}  ·  ${"%.0f".format(share * 100)}%", pageW - margin, y, right)
            y += 6f
            val w = pageW - 2 * margin
            page!!.canvas.drawRoundRect(RectF(margin, y, margin + w, y + 5f), 3f, 3f, track)
            page!!.canvas.drawRoundRect(RectF(margin, y, margin + w * share, y + 5f), 3f, 3f, bar)
            y += 18f
        }

        section("Income by category")
        val inc = ledger.byCategory(range, CategoryKind.INCOME)
        if (inc.isEmpty()) row("No income recorded", "", muted)
        inc.forEach { row(ledger.categoryName(it.categoryId), fmt(it.amount)) }

        section("Budgets")
        val reference = if (range.endInclusive.isAfter(LocalDate.now())) LocalDate.now() else range.endInclusive
        val statuses = ledger.budgetStatuses(reference)
        if (statuses.isEmpty()) row("No budgets set", "", muted)
        statuses.forEach { s ->
            val name = if (s.budget.categoryId == null) "Overall (${s.budget.period.label.lowercase()})" else ledger.categoryName(s.budget.categoryId)
            row(name, "${fmt(s.spent)} of ${fmt(s.limit)}${if (s.over) "  · over" else ""}")
        }

        section("Accounts")
        ledger.activeAccounts.forEach { a -> row("${a.name} (${a.type.label})", Money.format(ledger.balance(a.id), a.currency)) }

        section("Savings goals")
        val goals = ledger.goalProgress()
        if (goals.isEmpty()) row("No goals yet", "", muted)
        goals.forEach { g -> row(g.goal.name, "${Money.format(g.saved, g.goal.currency)} of ${Money.format(g.goal.targetMinor, g.goal.currency)} · ${g.percent}%") }

        section("Transactions")
        val txs = ledger.transactionsIn(range).sortedBy { it.tx.occurredAt }
        if (txs.isEmpty()) row("No transactions", "", muted)
        txs.forEach { t ->
            val d = ledger.dateOf(t).format(DateTimeFormatter.ofPattern("MMM d"))
            val cur = ledger.currencyOf(t.tx.accountId)
            val signed = when (t.tx.type) {
                TxType.INCOME -> t.tx.amountMinor
                TxType.EXPENSE -> -t.tx.amountMinor
                else -> t.tx.amountMinor
            }
            row("$d   ${ledger.titleOf(t).take(38)}", Money.format(signed, cur, signed = t.tx.type == TxType.INCOME))
        }

        page?.let { doc.finishPage(it) }
        resolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) } ?: error("Cannot open the selected file")
        doc.close()
    }

    /* ---------------- Exchange rates (optional, online) ---------------- */

    /** Downloads reference rates (units per 1 USD). Only ever called when the user taps "Update online". */
    suspend fun fetchOnlineRates(): Map<String, BigDecimal> = withContext(Dispatchers.IO) {
        val conn = (URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("Rate service returned ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optString("result") != "success") error("Rate service unavailable")
            val rates = root.getJSONObject("rates")
            val out = HashMap<String, BigDecimal>()
            rates.keys().forEach { key -> runCatching { out[key] = BigDecimal(rates.get(key).toString()) } }
            out
        } finally {
            conn.disconnect()
        }
    }
}
