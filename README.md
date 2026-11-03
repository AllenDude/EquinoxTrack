# EquinoxTrack

**Personal finance, in balance.** A native Android app (Kotlin + Jetpack Compose) for managing your *own*
money: accounts, income and expenses, budgets, bills, savings goals, net worth and a daily
**Safe-to-Spend** allowance. Fully offline, private by design, with four home-screen widgets.

> Default currency: **PHP (₱)** — changeable at any time.

---

## Features

| Area | What you get |
|---|---|
| **Dashboard** | Total balance hero, net worth, month income/expenses, Safe-to-Spend "horizon" gauge, quick actions, 6-month income-vs-expense chart, spending donut, budget progress (with pace marker), upcoming bills, recent activity, privacy mask (hide balances) |
| **Transactions** | Add / edit / delete income, expenses and transfers; built-in calculator keypad (`+ − × ÷`); categories; account selection; notes; date & time pickers; **split expenses** across categories; cross-currency transfers; search (notes, categories, accounts, amounts) and filters (type, account, category, period, custom range); day-grouped history |
| **Accounts** | Cash, Bank, E-wallet, Custom; opening balances; colours & icons; per-account currency; balances; 90-day balance trend; per-account history and monthly in/out; transfers; **balance adjustments** (never counted as income/expense); archive or delete |
| **Budgets** | Overall and per-category budgets, monthly or yearly; spent / remaining / utilisation; pace warnings; month navigation; yearly summary chart (spending vs monthly budget, months over budget); "average of last 3 months" suggestion |
| **Safe-to-Spend** | Daily allowance, what's left today, next-7-days and rest-of-period amounts, progress; transparent breakdown (spendable balances − unpaid bills − goal savings − cushion, capped by remaining budget); configurable budget-month start day (payday), cushion and goal protection |
| **Bills & subscriptions** | One-time, weekly, monthly, yearly (every *N*); start and end dates; paid / skipped / overdue states; pay (records exactly one expense), skip, undo; payment history; subscription monthly/yearly cost; reminders *N* days before |
| **Savings goals** | Multiple goals, target amount & date, contributions & withdrawals, % complete, remaining, suggested monthly saving |
| **Net worth & assets** | Accounts by type + manually tracked personal assets (property, vehicle, valuables…) with valuation history |
| **Insights** | Week / month / year with navigation; income vs expenses bars; spending *or* income by category (interactive donut); 6-period trend line; KPIs with change vs previous period; savings rate; highlights; 12-month history table; per-account analytics |
| **Import / export** | CSV export & import (with preview, duplicate detection, auto-creation of missing accounts/categories), full backup & restore (optional AES-256-GCM password encryption), monthly PDF report, yearly summary CSV |
| **Widgets** | Balance · Safe-to-Spend · Monthly summary · Quick actions (Expense / Income / Transfer). Real data, light & dark, resizable, work with the app closed, open the right screen when tapped |
| **Customisation & security** | Light / Dark / System theme, 6 accent palettes, optional biometric / device-credential app lock, hide-balances mode |

**Deliberately not included:** loans, lending/borrowing, debt tracking, credit cards, business
accounting, bank sync, investments/crypto, AI assistants, ads, analytics or accounts.

## Design

An original "equinox" identity — balance between light and dark:

* **Midnight navy / charcoal surfaces** with electric-violet (default) accents, subtle gradients
  and a single soft light source per hero card; faint orbital rings as the signature texture.
* **Horizon gauge** for Safe-to-Spend: a half-circle that fills as discretionary money is used,
  with a glowing "sun" marking how far through the budget period you are.
* **Inter / Inter Display** typography (bundled, SIL OFL) with tabular numerals so amounts align
  and don't jitter during count-up animations.
* Spring-based press feedback on every card, staggered section entrances, animated charts,
  floating bottom navigation and a gradient add button.
* The hero cards stay deep in light mode too, as the brand moment.

## Architecture

```
app/src/main/java/com/equinoxtrack/app
├── EquinoxApp.kt              Application + manual DI container (AppContainer)
├── MainActivity.kt            Single activity, biometric lock, widget deep links
├── domain/                    Pure Kotlin — no Android imports
│   ├── Money.kt               Minor-unit money, currency catalog, formatting/parsing
│   ├── Calculations.kt        Currency conversion, periods, balances, goals, net worth
│   ├── Planning.kt            Bill schedules, Safe-to-Spend, budgets
│   ├── Ledger.kt              Read model: every derived number in the app comes from here
│   ├── Tools.kt               Calculator, CSV codec, duplicate detection, backup crypto
│   └── Models.kt, Defaults.kt
├── data/
│   ├── db/                    Room entities, DAOs, database
│   ├── repo/                  FinanceRepository — all writes, transactional
│   ├── settings/              DataStore preferences
│   └── io/                    CSV import/export, backup/restore, PDF report, rates
├── ui/                        Compose screens + ViewModels (MVVM), design system, charts
├── widget/                    Jetpack Glance widgets + updater
└── notifications/             WorkManager: bill reminders, daily widget refresh
```

* **MVVM**: screens observe `StateFlow`s exposed by ViewModels; ViewModels map the shared
  `Ledger` read model (built from Room `Flow`s + DataStore settings) and call the repository.
* **Kotlin Coroutines & Flow** throughout; Room `Flow`s keep every screen and widget live.

### Financial accuracy rules (enforced in code and unit-tested)

* All money is stored as **`Long` minor units** (e.g. centavos); conversions use `BigDecimal`.
* **Balances are never stored.** They are derived from opening balance + ledger, so editing a
  transaction updates every affected balance and deleting one fully reverses it.
* **Transfers** debit the source and credit the destination (with a separate destination amount
  for cross-currency transfers) and are never income or expense. **Adjustments** likewise.
* **Savings goals are earmarks** inside your accounts: contributions are never income and are
  never added to net worth a second time.
* **Bill payments** create exactly one expense, linked to the occurrence they settle. That
  occurrence stops being reserved in Safe-to-Spend, so nothing is counted twice. Deleting the
  expense cascades and marks the occurrence unpaid again.
* **Net worth** = account balances (once) + separately tracked personal assets.
* Every multi-row write (save with splits, pay bill, import, restore, delete account) runs in a
  **Room database transaction**.
* Missing exchange rates are flagged instead of silently guessed.

## Building

### With GitHub Actions (recommended)

1. Create a new GitHub repository and upload the **contents** of this folder (including the
   hidden `.github` folder and `.gitignore`) to the `main` branch.
2. The **Android build** workflow (`.github/workflows/android-build.yml`) runs on every push to
   `main` and can be started manually from **Actions → Android build → Run workflow**.
3. It sets up JDK 17 and the Android SDK, caches Gradle, runs the unit tests, builds the debug
   APK and uploads it as the **`EquinoxTrack-debug-apk`** artifact (open the run → *Artifacts*).
   Test reports are uploaded as `unit-test-reports`.

> Uploading through the GitHub website: drag the folders in, or use *Add file → Upload files*.
> If your upload tool skips dot-folders, create `.github/workflows/android-build.yml` manually
> with *Add file → Create new file* and paste the workflow contents.

#### Optional signed release build

Add these repository secrets (*Settings → Secrets and variables → Actions*):

| Secret | Value |
|---|---|
| `EQX_KEYSTORE_BASE64` | `base64 -w0 release.jks` output |
| `EQX_KEYSTORE_PASSWORD` | keystore password |
| `EQX_KEY_ALIAS` | key alias |
| `EQX_KEY_PASSWORD` | key password (optional; defaults to the keystore password) |

When present, the workflow also builds a minified, signed release APK and uploads it as
`EquinoxTrack-release-apk`. Without them the release step is skipped.

Create a keystore once with:

```bash
keytool -genkeypair -v -keystore release.jks -alias equinox -keyalg RSA -keysize 2048 -validity 10000
```

### Locally

Open the folder in Android Studio (Ladybug or newer) or run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Requirements: JDK 17, Android SDK 35. The Gradle wrapper (8.11.1) downloads automatically.

Toolchain: AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 · Compose BOM 2024.12.01 · Room 2.6.1 ·
Glance 1.1.1 · WorkManager 2.10.0 · Navigation 2.8.5 · minSdk 26 · target/compile SDK 35.

## CSV format

```
Date,Time,Type,Account,To Account,Category,Splits,Amount,Currency,To Amount,Note
2026-03-01,08:15,Expense,Wallet,,Food & Dining,,185.00,PHP,,Breakfast
2026-03-01,12:30,Expense,GCash,,Split,Groceries=850.00|Personal Care=150.00,1000.00,PHP,,Weekly run
2026-03-02,09:00,Transfer,BPI Savings,GCash,,,2000.00,PHP,2000.00,
2026-03-15,17:00,Income,BPI Savings,,Salary,,25000.00,PHP,,
```

* Only **Date** and **Amount** are required. If *Type* is empty, negative amounts are expenses.
* Accepted dates: `YYYY-MM-DD`, `M/D/YYYY`, `D.M.YYYY`, `YYYY/M/D`.
* Missing accounts and categories are created during import (you see the list first).
* A row is flagged as a duplicate when date, type, account, amount and note all match an
  existing transaction (or an earlier row in the same file). You choose whether to skip them.

## Privacy

* No sign-up, no ads, no analytics, no bank connections. All data stays in the on-device
  Room database; Android cloud backup and device-transfer are disabled for it.
* The only network request is the optional **Exchange rates → Update** button
  (`open.er-api.com`). Everything else works with no connection.
* Backups are files you choose where to save; with a password they are encrypted with
  AES-256-GCM using a PBKDF2-SHA256 derived key.

## Tests

`app/src/test` contains 45 JVM unit tests covering money parsing/formatting and rounding,
currency conversion, the calculator, CSV round-trips and error reporting, duplicate detection,
backup encryption, balances (income/expense/transfer/adjustment, edit and delete reversal,
cross-currency), transfers/adjustments excluded from totals, goal contributions not counted as
income or double-counted in net worth, assets in net worth, multi-currency totals, split
category totals, budget status, bill schedules (month-end handling, overdue, skipped, undo,
end dates), bill payments not double-counted in Safe-to-Spend, goal protection and
Safe-to-Spend maths.

## Licences

App code: yours to use. Bundled Inter fonts © The Inter Project Authors, SIL Open Font
License 1.1 (see `licenses/Inter-OFL.txt`).
