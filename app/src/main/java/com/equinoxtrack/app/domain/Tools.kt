package com.equinoxtrack.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/* ------------------------------------------------------------------ */
/* Calculator for the amount keypad                                    */
/* ------------------------------------------------------------------ */

object AmountExpression {
    private val operators = setOf('+', '-', '×', '÷')

    fun isOperator(c: Char) = c in operators

    /** Normalises typed operators to the display set. */
    fun normalize(expr: String): String = expr.replace('*', '×').replace('x', '×').replace('/', '÷')

    fun hasOperator(expr: String): Boolean = normalize(expr).drop(1).any { it in operators }

    /**
     * Evaluates an expression such as "120+35.5×2" with normal precedence. Returns null when the
     * expression is incomplete or divides by zero. Trailing operators are ignored.
     */
    fun evaluate(raw: String): BigDecimal? {
        var expr = normalize(raw.replace(",", "").replace(" ", ""))
        while (expr.isNotEmpty() && (expr.last() in operators || expr.last() == '.')) expr = expr.dropLast(1)
        if (expr.isEmpty()) return null
        val numbers = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        var i = 0
        var expectNumber = true
        while (i < expr.length) {
            val c = expr[i]
            if (expectNumber) {
                val start = i
                if (c == '-' || c == '+') i++
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                val token = expr.substring(start, i)
                if (token.isEmpty() || token == "-" || token == "+" || token.count { it == '.' } > 1) return null
                numbers += runCatching { BigDecimal(token) }.getOrNull() ?: return null
                expectNumber = false
            } else {
                if (c !in operators) return null
                ops += c
                i++
                expectNumber = true
            }
        }
        if (numbers.size != ops.size + 1) return null
        // First pass: × and ÷
        val n2 = arrayListOf(numbers[0])
        val o2 = ArrayList<Char>()
        for (k in ops.indices) {
            val op = ops[k]
            val next = numbers[k + 1]
            when (op) {
                '×' -> n2[n2.lastIndex] = n2.last().multiply(next, MathContext.DECIMAL64)
                '÷' -> {
                    if (next.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(next, MathContext.DECIMAL64)
                }
                else -> { o2 += op; n2 += next }
            }
        }
        var result = n2[0]
        for (k in o2.indices) {
            result = if (o2[k] == '+') result.add(n2[k + 1]) else result.subtract(n2[k + 1])
        }
        return result
    }

    /** Applies a keypad key to the current expression, enforcing sane input. */
    fun press(current: String, key: Char, maxDecimals: Int): String {
        val expr = current
        val lastToken = expr.takeLastWhile { it !in operators }
        return when {
            key.isDigit() -> {
                if (lastToken.contains('.') && lastToken.substringAfter('.').length >= maxDecimals) expr
                else if (lastToken == "0") expr.dropLast(1) + key
                else if (expr.length >= 24) expr
                else expr + key
            }
            key == '.' -> when {
                maxDecimals == 0 || lastToken.contains('.') -> expr
                lastToken.isEmpty() -> expr + "0."
                else -> "$expr."
            }
            key in operators -> when {
                expr.isEmpty() -> expr
                expr.last() in operators -> expr.dropLast(1) + key
                expr.last() == '.' -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> expr
        }
    }
}

/* ------------------------------------------------------------------ */
/* CSV                                                                 */
/* ------------------------------------------------------------------ */

object Csv {
    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(" ") || value.endsWith(" ")
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(values: List<String>): String = values.joinToString(",") { escape(it) }

    /** RFC 4180 parser supporting quoted fields, escaped quotes and newlines inside quotes. */
    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.setLength(0) }
                '\r' -> Unit
                '\n' -> { row += field.toString(); field.setLength(0); rows += row; row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

/** A transaction as it appears in an imported/exported CSV file. */
data class CsvTransaction(
    val date: LocalDate,
    val time: LocalTime,
    val type: TxType,
    val account: String,
    val toAccount: String,
    val category: String,
    /** Split allocations as (category, decimal amount). Empty when not split. */
    val splits: List<Pair<String, BigDecimal>>,
    val amount: BigDecimal,
    val currency: String,
    val toAmount: BigDecimal?,
    val note: String,
)

data class CsvParseResult(val rows: List<CsvTransaction>, val errors: List<String>)

object TransactionCsv {
    val header = listOf("Date", "Time", "Type", "Account", "To Account", "Category", "Splits", "Amount", "Currency", "To Amount", "Note")
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val altDates = listOf(
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),
    )

    fun write(rows: List<CsvTransaction>): String = buildString {
        append(Csv.row(header)).append("\n")
        rows.forEach { r ->
            append(
                Csv.row(
                    listOf(
                        r.date.format(dateFmt),
                        r.time.format(timeFmt),
                        r.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        r.account,
                        r.toAccount,
                        r.category,
                        r.splits.joinToString("|") { "${it.first}=${it.second.toPlainString()}" },
                        r.amount.toPlainString(),
                        r.currency,
                        r.toAmount?.toPlainString() ?: "",
                        r.note,
                    )
                )
            ).append("\n")
        }
    }

    private fun parseDate(s: String): LocalDate? {
        val t = s.trim().take(10).takeIf { it.isNotEmpty() } ?: return null
        runCatching { return LocalDate.parse(t, dateFmt) }
        for (f in altDates) runCatching { return LocalDate.parse(s.trim(), f) }
        return null
    }

    private fun parseAmount(s: String): BigDecimal? {
        val cleaned = s.trim().replace(",", "").filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-") return null
        return runCatching { BigDecimal(cleaned) }.getOrNull()
    }

    /** Parses CSV text; unknown columns are ignored, header names are matched case-insensitively. */
    fun parse(text: String, defaultCurrency: String): CsvParseResult {
        val table = Csv.parse(text)
        if (table.isEmpty()) return CsvParseResult(emptyList(), listOf("The file is empty."))
        val head = table.first().map { it.trim().lowercase() }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } } ?: -1
        val iDate = idx("date")
        val iTime = idx("time")
        val iType = idx("type")
        val iAccount = idx("account")
        val iTo = idx("to account", "to_account")
        val iCategory = idx("category")
        val iSplits = idx("splits")
        val iAmount = idx("amount")
        val iCurrency = idx("currency")
        val iToAmount = idx("to amount", "to_amount")
        val iNote = idx("note", "notes", "description")
        if (iDate < 0 || iAmount < 0) {
            return CsvParseResult(emptyList(), listOf("Missing required columns. Expected at least Date and Amount."))
        }
        val rows = ArrayList<CsvTransaction>()
        val errors = ArrayList<String>()
        table.drop(1).forEachIndexed { n, r ->
            val line = n + 2
            fun col(i: Int) = if (i >= 0 && i < r.size) r[i].trim() else ""
            val date = parseDate(col(iDate))
            val rawAmount = parseAmount(col(iAmount))
            if (date == null) { errors += "Line $line: invalid date \"${col(iDate)}\""; return@forEachIndexed }
            if (rawAmount == null) { errors += "Line $line: invalid amount \"${col(iAmount)}\""; return@forEachIndexed }
            val time = runCatching { LocalTime.parse(col(iTime).take(5), timeFmt) }.getOrDefault(LocalTime.NOON)
            val typeText = col(iType).lowercase()
            val type = when {
                typeText.startsWith("inc") -> TxType.INCOME
                typeText.startsWith("exp") -> TxType.EXPENSE
                typeText.startsWith("tra") -> TxType.TRANSFER
                typeText.startsWith("adj") -> TxType.ADJUSTMENT
                typeText.isEmpty() -> if (rawAmount.signum() < 0) TxType.EXPENSE else TxType.INCOME
                else -> { errors += "Line $line: unknown type \"${col(iType)}\""; return@forEachIndexed }
            }
            val amount = if (type == TxType.ADJUSTMENT) rawAmount else rawAmount.abs()
            if (amount.signum() == 0) { errors += "Line $line: zero amount"; return@forEachIndexed }
            val account = col(iAccount).ifBlank { "Imported" }
            val toAccount = col(iTo)
            if (type == TxType.TRANSFER && toAccount.isBlank()) { errors += "Line $line: transfer without destination account"; return@forEachIndexed }
            val splits = col(iSplits).split('|').mapNotNull { part ->
                val name = part.substringBeforeLast('=', "").trim()
                val value = parseAmount(part.substringAfterLast('=', ""))
                if (name.isEmpty() || value == null) null else name to value.abs()
            }
            val currency = col(iCurrency).uppercase().ifBlank { defaultCurrency }.take(3)
            rows += CsvTransaction(
                date, time, type, account, toAccount, col(iCategory), splits, amount, currency,
                parseAmount(col(iToAmount))?.abs(), col(iNote),
            )
        }
        return CsvParseResult(rows, errors)
    }
}

/* ------------------------------------------------------------------ */
/* Duplicate detection                                                 */
/* ------------------------------------------------------------------ */

object DuplicateDetector {
    /** Two records with the same day, type, account, amount and note are treated as duplicates. */
    fun fingerprint(date: LocalDate, type: TxType, account: String, amountMinor: Long, note: String): String =
        "${date.toEpochDay()}|${type.name}|${account.trim().lowercase()}|$amountMinor|${note.trim().lowercase()}"

    /** Returns, for each candidate, whether it duplicates an existing record or an earlier candidate. */
    fun flag(existing: Set<String>, candidates: List<String>): List<Boolean> {
        val seen = HashSet(existing)
        return candidates.map { fp -> !seen.add(fp) }
    }
}

/* ------------------------------------------------------------------ */
/* Backup encryption                                                   */
/* ------------------------------------------------------------------ */

object BackupCrypto {
    private val MAGIC = "EQXB1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 120_000

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    /** AES-256-GCM with a PBKDF2-derived key. Output: MAGIC | salt | iv | ciphertext+tag. */
    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** Throws when the password is wrong or the data was tampered with. */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        require(isEncrypted(data)) { "Not an encrypted EquinoxTrack backup" }
        var p = MAGIC.size
        val salt = data.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val iv = data.copyOfRange(p, p + IV_LEN); p += IV_LEN
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password.toCharArray(), salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(data.copyOfRange(p, data.size))
    }
}
