package com.equinoxtrack.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/** Metadata about a currency the app can display. */
data class CurrencyInfo(val code: String, val name: String, val symbol: String, val digits: Int)

object CurrencyCatalog {
    private val known = listOf(
        CurrencyInfo("PHP", "Philippine Peso", "₱", 2),
        CurrencyInfo("USD", "US Dollar", "$", 2),
        CurrencyInfo("EUR", "Euro", "€", 2),
        CurrencyInfo("GBP", "British Pound", "£", 2),
        CurrencyInfo("JPY", "Japanese Yen", "¥", 0),
        CurrencyInfo("KRW", "South Korean Won", "₩", 0),
        CurrencyInfo("CNY", "Chinese Yuan", "CN¥", 2),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$", 2),
        CurrencyInfo("TWD", "New Taiwan Dollar", "NT$", 2),
        CurrencyInfo("SGD", "Singapore Dollar", "S$", 2),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM", 2),
        CurrencyInfo("THB", "Thai Baht", "฿", 2),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp", 2),
        CurrencyInfo("VND", "Vietnamese Dong", "₫", 0),
        CurrencyInfo("INR", "Indian Rupee", "₹", 2),
        CurrencyInfo("AUD", "Australian Dollar", "A$", 2),
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", 2),
        CurrencyInfo("CAD", "Canadian Dollar", "C$", 2),
        CurrencyInfo("CHF", "Swiss Franc", "CHF", 2),
        CurrencyInfo("AED", "UAE Dirham", "AED", 2),
        CurrencyInfo("SAR", "Saudi Riyal", "SAR", 2),
        CurrencyInfo("QAR", "Qatari Riyal", "QAR", 2),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KWD", 3),
        CurrencyInfo("BRL", "Brazilian Real", "R$", 2),
        CurrencyInfo("MXN", "Mexican Peso", "MX$", 2),
        CurrencyInfo("ZAR", "South African Rand", "R", 2),
        CurrencyInfo("SEK", "Swedish Krona", "kr", 2),
        CurrencyInfo("NOK", "Norwegian Krone", "kr", 2),
        CurrencyInfo("DKK", "Danish Krone", "kr", 2),
        CurrencyInfo("PLN", "Polish Złoty", "zł", 2),
        CurrencyInfo("TRY", "Turkish Lira", "₺", 2),
    )
    private val byCode = known.associateBy { it.code }

    val all: List<CurrencyInfo> get() = known

    fun info(code: String): CurrencyInfo {
        byCode[code]?.let { return it }
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrNull()
            ?.takeIf { it >= 0 } ?: 2
        val name = runCatching { Currency.getInstance(code).getDisplayName(Locale.US) }.getOrNull() ?: code
        return CurrencyInfo(code, name, code, digits)
    }

    fun digits(code: String): Int = info(code).digits
}

/** Helpers for integer minor-unit money. All arithmetic is exact; formatting goes through BigDecimal. */
object Money {

    fun toDecimal(minor: Long, currency: String): BigDecimal =
        BigDecimal.valueOf(minor, CurrencyCatalog.digits(currency))

    /** Converts a decimal amount to minor units, rounding half-even to the currency's precision. */
    fun toMinor(amount: BigDecimal, currency: String): Long =
        amount.setScale(CurrencyCatalog.digits(currency), RoundingMode.HALF_EVEN).unscaledValue().toLong()

    /** Parses free text like "1,234.50", "₱ 99" or "-12" into minor units. Returns null when invalid. */
    fun parseToMinor(text: String, currency: String): Long? {
        val cleaned = text.trim()
            .replace(",", "")
            .replace(" ", "")
            .filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        return runCatching { toMinor(BigDecimal(cleaned), currency) }.getOrNull()
    }

    /** Plain decimal string without grouping, e.g. 123456 PHP -> "1234.56". Used for CSV and inputs. */
    fun plain(minor: Long, currency: String): String = toDecimal(minor, currency).toPlainString()

    /** Editable representation without trailing zeros: 120000 -> "1200", 120050 -> "1200.5". */
    fun editable(minor: Long, currency: String): String {
        if (minor == 0L) return ""
        val stripped = toDecimal(minor, currency).stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }

    private fun pattern(digits: Int): DecimalFormat {
        val pattern = if (digits > 0) "#,##0." + "0".repeat(digits) else "#,##0"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_EVEN
        }
    }

    /**
     * Formats minor units with the currency symbol, e.g. "₱1,234.56" or "-₱20.00".
     * [signed] forces a leading "+" for positive values.
     */
    fun format(minor: Long, currency: String, signed: Boolean = false, compact: Boolean = false): String {
        val info = CurrencyCatalog.info(currency)
        val value = toDecimal(minor, currency).abs()
        val body = if (compact) compactBody(value, info.digits) else pattern(info.digits).format(value)
        val sign = when {
            minor < 0 -> "-"
            signed && minor > 0 -> "+"
            else -> ""
        }
        val sym = info.symbol
        val separator = if (sym.length > 1 && sym.all { it.isLetter() }) " " else ""
        return "$sign$sym$separator$body"
    }

    private fun compactBody(value: BigDecimal, digits: Int): String {
        val thousand = BigDecimal(1_000)
        val million = BigDecimal(1_000_000)
        val billion = BigDecimal(1_000_000_000)
        val oneDecimal = DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_EVEN
        }
        return when {
            value >= billion -> oneDecimal.format(value.divide(billion)) + "B"
            value >= million -> oneDecimal.format(value.divide(million)) + "M"
            value >= BigDecimal(10_000) -> oneDecimal.format(value.divide(thousand)) + "K"
            else -> pattern(digits).format(value)
        }
    }

    /** Hidden representation used when balances are masked. */
    fun masked(currency: String): String = CurrencyCatalog.info(currency).symbol + "••••"
}
