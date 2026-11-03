package com.equinoxtrack.app.domain

/** Seed data and the colour swatches offered in pickers (ARGB longs so the domain stays Android-free). */
object Defaults {
    val swatches: List<Long> = listOf(
        0xFF7C5CFF, 0xFF4F8BFF, 0xFF22D3EE, 0xFF14D3B4, 0xFF2EE59D, 0xFFA3E635,
        0xFFFFC145, 0xFFFF8A4C, 0xFFFF5C7A, 0xFFFF4F9A, 0xFFE15BFF, 0xFF94A3B8,
    )

    data class SeedCategory(val name: String, val kind: CategoryKind, val icon: String, val color: Long)

    val categories: List<SeedCategory> = listOf(
        SeedCategory("Food & Dining", CategoryKind.EXPENSE, "restaurant", 0xFFFF8A4C),
        SeedCategory("Groceries", CategoryKind.EXPENSE, "grocery", 0xFF14D3B4),
        SeedCategory("Transport", CategoryKind.EXPENSE, "bus", 0xFF4F8BFF),
        SeedCategory("Housing", CategoryKind.EXPENSE, "home", 0xFF9B7BFF),
        SeedCategory("Utilities", CategoryKind.EXPENSE, "bolt", 0xFFFFC145),
        SeedCategory("Subscriptions", CategoryKind.EXPENSE, "subscriptions", 0xFFE15BFF),
        SeedCategory("Shopping", CategoryKind.EXPENSE, "bag", 0xFFFF4F9A),
        SeedCategory("Health", CategoryKind.EXPENSE, "health", 0xFF2EE59D),
        SeedCategory("Entertainment", CategoryKind.EXPENSE, "game", 0xFF7C5CFF),
        SeedCategory("Education", CategoryKind.EXPENSE, "school", 0xFF22D3EE),
        SeedCategory("Personal Care", CategoryKind.EXPENSE, "spa", 0xFFFF5C7A),
        SeedCategory("Travel", CategoryKind.EXPENSE, "flight", 0xFF38BDF8),
        SeedCategory("Gifts & Giving", CategoryKind.EXPENSE, "gift", 0xFFFB7185),
        SeedCategory("Family & Pets", CategoryKind.EXPENSE, "pets", 0xFFA3E635),
        SeedCategory("Other", CategoryKind.EXPENSE, "more", 0xFF94A3B8),
        SeedCategory("Salary", CategoryKind.INCOME, "work", 0xFF2EE59D),
        SeedCategory("Freelance", CategoryKind.INCOME, "laptop", 0xFF14D3B4),
        SeedCategory("Side Income", CategoryKind.INCOME, "store", 0xFF4F8BFF),
        SeedCategory("Gifts Received", CategoryKind.INCOME, "gift", 0xFFFB7185),
        SeedCategory("Refunds", CategoryKind.INCOME, "refund", 0xFFFFC145),
        SeedCategory("Interest", CategoryKind.INCOME, "trending", 0xFF7C5CFF),
        SeedCategory("Other Income", CategoryKind.INCOME, "more", 0xFF94A3B8),
    )

    fun accountIcon(type: AccountType): String = when (type) {
        AccountType.CASH -> "cash"
        AccountType.BANK -> "bank"
        AccountType.EWALLET -> "phone"
        AccountType.CUSTOM -> "wallet"
    }

    fun accountColor(type: AccountType): Long = when (type) {
        AccountType.CASH -> 0xFF2EE59D
        AccountType.BANK -> 0xFF4F8BFF
        AccountType.EWALLET -> 0xFF7C5CFF
        AccountType.CUSTOM -> 0xFFFFC145
    }
}
