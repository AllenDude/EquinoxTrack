package com.equinoxtrack.app

import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.domain.AmountExpression
import com.equinoxtrack.app.domain.BackupCrypto
import com.equinoxtrack.app.domain.Csv
import com.equinoxtrack.app.domain.CsvTransaction
import com.equinoxtrack.app.domain.CurrencyConverter
import com.equinoxtrack.app.domain.DuplicateDetector
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.TransactionCsv
import com.equinoxtrack.app.domain.TxType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class MoneyTest {
    @Test fun formatsPesoWithSymbolAndGrouping() {
        assertEquals("₱1,234.56", Money.format(123456, "PHP"))
        assertEquals("-₱0.05", Money.format(-5, "PHP"))
        assertEquals("+₱10.00", Money.format(1000, "PHP", signed = true))
    }

    @Test fun zeroDecimalCurrencies() {
        assertEquals("¥1,500", Money.format(1500, "JPY"))
        assertEquals(1500L, Money.parseToMinor("1,500", "JPY"))
    }

    @Test fun parsesUserInputExactly() {
        assertEquals(123450L, Money.parseToMinor("1,234.5", "PHP"))
        assertEquals(10L, Money.parseToMinor("0.10", "PHP"))
        assertEquals(-2000L, Money.parseToMinor("-20", "PHP"))
        assertNull(Money.parseToMinor("abc", "PHP"))
        // 0.1 + 0.2 must be exactly 0.30 in minor units
        assertEquals(30L, Money.parseToMinor("0.1", "PHP")!! + Money.parseToMinor("0.2", "PHP")!!)
    }

    @Test fun roundsHalfEven() {
        assertEquals(2L, Money.toMinor(BigDecimal("0.025"), "PHP"))
        assertEquals(4L, Money.toMinor(BigDecimal("0.035"), "PHP"))
    }

    @Test fun editableDropsTrailingZeros() {
        assertEquals("1200", Money.editable(120000, "PHP"))
        assertEquals("1200.5", Money.editable(120050, "PHP"))
    }

    @Test fun compactFormatting() {
        assertEquals("₱1.2M", Money.format(123_456_789, "PHP", compact = true))
        assertEquals("₱25K", Money.format(2_500_000, "PHP", compact = true))
    }
}

class CurrencyConverterTest {
    private val rates = listOf(
        ExchangeRateEntity("USD", "1"),
        ExchangeRateEntity("PHP", "58.00"),
        ExchangeRateEntity("JPY", "150"),
    )

    @Test fun convertsThroughReference() {
        val c = CurrencyConverter(rates)
        assertEquals(580000L, c.convert(10000, "USD", "PHP")) // $100 -> ₱5,800.00
        assertEquals(15000L, c.convert(10000, "USD", "JPY")) // $100 -> ¥15,000
        assertEquals(1000L, c.convert(1000, "PHP", "PHP"))
    }

    @Test fun missingRateReturnsNull() {
        assertNull(CurrencyConverter(rates).convert(100, "EUR", "PHP"))
    }

    @Test fun manualRateRelativeToBase() {
        // "1 EUR = 63.50 PHP" with PHP stored at 58 per reference
        val eur = CurrencyConverter.perReferenceFor(BigDecimal("63.50"), BigDecimal("58"))
        val c = CurrencyConverter(rates + ExchangeRateEntity("EUR", eur.toPlainString()))
        assertEquals(635000L, c.convert(10000, "EUR", "PHP"))
    }
}

class AmountExpressionTest {
    @Test fun respectsPrecedence() {
        assertEquals(0, BigDecimal("191").compareTo(AmountExpression.evaluate("120+35.5×2")))
        assertEquals(0, BigDecimal("50").compareTo(AmountExpression.evaluate("100÷2")))
        assertEquals(0, BigDecimal("70").compareTo(AmountExpression.evaluate("100-30")))
    }

    @Test fun ignoresTrailingOperatorAndRejectsDivZero() {
        assertEquals(0, BigDecimal("12").compareTo(AmountExpression.evaluate("12+")))
        assertNull(AmountExpression.evaluate("5÷0"))
        assertNull(AmountExpression.evaluate(""))
    }

    @Test fun keypadLimitsDecimals() {
        var e = ""
        "12.345".forEach { e = AmountExpression.press(e, it, 2) }
        assertEquals("12.34", e)
        e = AmountExpression.press(e, '+', 2)
        e = AmountExpression.press(e, '×', 2)
        assertEquals("12.34×", e)
        assertEquals("0.", AmountExpression.press("", '.', 2))
        assertEquals("5", AmountExpression.press("0", '5', 2))
    }
}

class CsvTest {
    @Test fun parsesQuotedFields() {
        val rows = Csv.parse("a,\"b, c\",\"say \"\"hi\"\"\"\n1,\"multi\nline\",3\n")
        assertEquals(listOf("a", "b, c", "say \"hi\""), rows[0])
        assertEquals(listOf("1", "multi\nline", "3"), rows[1])
    }

    @Test fun roundTripsTransactions() {
        val original = listOf(
            CsvTransaction(LocalDate.of(2026, 1, 5), LocalTime.of(9, 30), TxType.EXPENSE, "Wallet", "", "Split",
                listOf("Food" to BigDecimal("120.50"), "Transport" to BigDecimal("30.00")), BigDecimal("150.50"), "PHP", null, "Lunch, then cab"),
            CsvTransaction(LocalDate.of(2026, 1, 6), LocalTime.of(18, 0), TxType.TRANSFER, "Bank", "Wallet", "",
                emptyList(), BigDecimal("1000.00"), "PHP", BigDecimal("1000.00"), ""),
        )
        val parsed = TransactionCsv.parse(TransactionCsv.write(original), "PHP")
        assertTrue(parsed.errors.isEmpty())
        assertEquals(2, parsed.rows.size)
        assertEquals(original[0].splits, parsed.rows[0].splits)
        assertEquals("Lunch, then cab", parsed.rows[0].note)
        assertEquals(TxType.TRANSFER, parsed.rows[1].type)
        assertEquals("Wallet", parsed.rows[1].toAccount)
    }

    @Test fun reportsBadRows() {
        val parsed = TransactionCsv.parse("Date,Amount,Type\n2026-01-01,abc,Expense\nnope,5,Expense\n2026-01-02,-5,\n", "PHP")
        assertEquals(2, parsed.errors.size)
        assertEquals(1, parsed.rows.size)
        assertEquals(TxType.EXPENSE, parsed.rows[0].type)
        assertEquals(0, BigDecimal("5").compareTo(parsed.rows[0].amount))
    }

    @Test fun flagsDuplicates() {
        val d = LocalDate.of(2026, 2, 1)
        val existing = setOf(DuplicateDetector.fingerprint(d, TxType.EXPENSE, "Wallet", 500, "Coffee"))
        val candidates = listOf(
            DuplicateDetector.fingerprint(d, TxType.EXPENSE, "wallet ", 500, " coffee"),
            DuplicateDetector.fingerprint(d, TxType.EXPENSE, "Wallet", 900, "Lunch"),
            DuplicateDetector.fingerprint(d, TxType.EXPENSE, "Wallet", 900, "Lunch"),
        )
        assertEquals(listOf(true, false, true), DuplicateDetector.flag(existing, candidates))
    }
}

class BackupCryptoTest {
    @Test fun encryptsAndDecrypts() {
        val plain = "{\"hello\":\"world\"}".toByteArray()
        val enc = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse"))
    }

    @Test fun wrongPasswordFails() {
        val enc = BackupCrypto.encrypt("secret".toByteArray(), "a")
        try {
            BackupCrypto.decrypt(enc, "b")
            fail("Expected failure")
        } catch (_: Exception) {
        }
    }
}
