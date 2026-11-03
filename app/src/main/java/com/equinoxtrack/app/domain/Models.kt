package com.equinoxtrack.app.domain

/** Kind of money movement. Only INCOME and EXPENSE ever count toward income/expense totals. */
enum class TxType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

enum class AccountType(val label: String) {
    CASH("Cash"),
    BANK("Bank"),
    EWALLET("E-wallet"),
    CUSTOM("Custom"),
}

enum class CategoryKind { INCOME, EXPENSE }

enum class BillFrequency(val label: String) {
    ONCE("One time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

enum class BudgetPeriod(val label: String) { MONTHLY("Monthly"), YEARLY("Yearly") }

enum class AssetKind(val label: String) {
    PROPERTY("Property"),
    VEHICLE("Vehicle"),
    VALUABLES("Valuables"),
    COLLECTIBLES("Collectibles"),
    OTHER("Other"),
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class AccentPalette(val label: String) {
    VIOLET("Violet"),
    ELECTRIC("Electric"),
    AURORA("Aurora"),
    EMBER("Ember"),
    ROSE("Rose"),
    LIME("Lime"),
}

/** User preferences persisted in DataStore. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPalette = AccentPalette.VIOLET,
    val baseCurrency: String = "PHP",
    val biometricLock: Boolean = false,
    val billReminders: Boolean = true,
    /** When true, money saved toward goals is treated as reserved and excluded from Safe-to-Spend. */
    val protectGoals: Boolean = true,
    /** A cushion kept aside from Safe-to-Spend, in base-currency minor units. */
    val bufferMinor: Long = 0L,
    /** Day of month on which the budget period starts (1-28). */
    val periodStartDay: Int = 1,
    val hideBalances: Boolean = false,
    val onboardingDone: Boolean = false,
)
