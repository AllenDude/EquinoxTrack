package com.equinoxtrack.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.equinoxtrack.app.MainActivity
import com.equinoxtrack.app.R
import com.equinoxtrack.app.appContainer
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.ui.DeepLinks
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/* ------------------------------------------------------------------ */
/* Snapshot of the numbers widgets show, persisted in Glance state     */
/* ------------------------------------------------------------------ */

data class WidgetSnapshot(
    val updatedAt: Long,
    val balance: String,
    val netWorth: String,
    val safeToday: String,
    val safeDaily: String,
    val safeWeekly: String,
    val safeDetail: String,
    val safeProgress: Float,
    val month: String,
    val income: String,
    val expense: String,
    val remainingLabel: String,
    val remaining: String,
    val budgetProgress: Float,
    val updatedLabel: String,
) {
    fun writeTo(p: MutablePreferences) {
        p[K.updatedAt] = updatedAt
        p[K.balance] = balance
        p[K.netWorth] = netWorth
        p[K.safeToday] = safeToday
        p[K.safeDaily] = safeDaily
        p[K.safeWeekly] = safeWeekly
        p[K.safeDetail] = safeDetail
        p[K.safeProgress] = safeProgress
        p[K.month] = month
        p[K.income] = income
        p[K.expense] = expense
        p[K.remainingLabel] = remainingLabel
        p[K.remaining] = remaining
        p[K.budgetProgress] = budgetProgress
        p[K.updatedLabel] = updatedLabel
    }

    private object K {
        val updatedAt = longPreferencesKey("w_updated_at")
        val balance = stringPreferencesKey("w_balance")
        val netWorth = stringPreferencesKey("w_net_worth")
        val safeToday = stringPreferencesKey("w_safe_today")
        val safeDaily = stringPreferencesKey("w_safe_daily")
        val safeWeekly = stringPreferencesKey("w_safe_weekly")
        val safeDetail = stringPreferencesKey("w_safe_detail")
        val safeProgress = floatPreferencesKey("w_safe_progress")
        val month = stringPreferencesKey("w_month")
        val income = stringPreferencesKey("w_income")
        val expense = stringPreferencesKey("w_expense")
        val remainingLabel = stringPreferencesKey("w_remaining_label")
        val remaining = stringPreferencesKey("w_remaining")
        val budgetProgress = floatPreferencesKey("w_budget_progress")
        val updatedLabel = stringPreferencesKey("w_updated_label")
    }

    companion object {
        fun read(p: Preferences): WidgetSnapshot? {
            val at = p[K.updatedAt] ?: return null
            return WidgetSnapshot(
                at, p[K.balance] ?: "", p[K.netWorth] ?: "", p[K.safeToday] ?: "", p[K.safeDaily] ?: "", p[K.safeWeekly] ?: "",
                p[K.safeDetail] ?: "", p[K.safeProgress] ?: 0f, p[K.month] ?: "", p[K.income] ?: "", p[K.expense] ?: "",
                p[K.remainingLabel] ?: "", p[K.remaining] ?: "", p[K.budgetProgress] ?: 0f, p[K.updatedLabel] ?: "",
            )
        }

        fun compute(l: Ledger): WidgetSnapshot {
            val hide = l.settings.hideBalances
            fun f(v: Long) = if (hide) Money.masked(l.base) else Money.format(v, l.base)
            fun fc(v: Long) = if (hide) Money.masked(l.base) else Money.format(v, l.base, compact = true)
            val safe = l.safeToSpend
            val month = Periods.month(YearMonth.from(l.today))
            val totals = l.totals(month)
            val overall = l.overallMonthlyBudget?.let { l.budgetStatus(it) }
            return WidgetSnapshot(
                updatedAt = System.currentTimeMillis(),
                balance = f(l.totalBalance),
                netWorth = "Net worth " + fc(l.netWorth.total),
                safeToday = f(safe.leftToday.coerceAtLeast(0)),
                safeDaily = fc(safe.daily),
                safeWeekly = fc(safe.weekly),
                safeDetail = "${fc(safe.daily)}/day · ${safe.daysLeft} days left",
                safeProgress = safe.progress,
                month = l.today.format(DateTimeFormatter.ofPattern("MMMM")),
                income = fc(totals.income),
                expense = fc(totals.expense),
                remainingLabel = if (overall != null) "Budget left" else "Net",
                remaining = if (overall != null) fc(overall.remaining) else fc(totals.net),
                budgetProgress = overall?.fraction?.coerceIn(0f, 1f) ?: 0f,
                updatedLabel = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
            )
        }

        suspend fun load(context: Context): WidgetSnapshot? =
            runCatching { compute(context.appContainer.loadLedger()) }.getOrNull()
    }
}

/* ------------------------------------------------------------------ */
/* Shared styling                                                      */
/* ------------------------------------------------------------------ */

private object W {
    val textPrimary = DayNight(day = Color(0xFF0C1030), night = Color(0xFFEEF1FF))
    val textSecondary = DayNight(day = Color(0xFF555C7E), night = Color(0xFF9AA3C7))
    val accent = DayNight(day = Color(0xFF6A4DF5), night = Color(0xFF8B6CFF))
    val income = DayNight(day = Color(0xFF0E9F6E), night = Color(0xFF34E3A0))
    val expense = DayNight(day = Color(0xFFE5395B), night = Color(0xFFFF6B86))
    val track = DayNight(day = Color(0xFFE3E6F2), night = Color(0xFF1F2742))
    val tileBg = DayNight(day = Color(0xFFF1F2FA), night = Color(0xFF161C33))
    val white = ColorProvider(Color.White)
    val whiteMuted = ColorProvider(Color(0xB3FFFFFF))
    val heroTrack = ColorProvider(Color(0x33FFFFFF))
}

private fun openIntent(context: Context, destination: String): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("equinoxtrack://widget/$destination"))
            .putExtra(DeepLinks.EXTRA, destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )

private val placeholder = WidgetSnapshot(0, "—", "", "—", "—", "—", "Open EquinoxTrack to set up", 0f, "", "—", "—", "Net", "—", 0f, "")

/** Reads the freshest snapshot: the one computed when the session started or a newer one pushed via state. */
@Composable
private fun rememberSnapshot(initial: WidgetSnapshot?): WidgetSnapshot {
    val fromState = WidgetSnapshot.read(currentState<Preferences>())
    return listOfNotNull(initial, fromState).maxByOrNull { it.updatedAt } ?: placeholder
}

private suspend fun GlanceAppWidget.prepare(context: Context, id: GlanceId): WidgetSnapshot? {
    val snap = WidgetSnapshot.load(context)
    if (snap != null) updateAppWidgetState(context, id) { snap.writeTo(it) }
    return snap
}

/* ------------------------------------------------------------------ */
/* 1. Balance                                                          */
/* ------------------------------------------------------------------ */

class BalanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 60.dp), DpSize(220.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = prepare(context, id)
        provideContent {
            val s = rememberSnapshot(initial)
            val big = LocalSize.current.height >= 100.dp
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_hero_bg))
                    .padding(16.dp).clickable(openIntent(context, DeepLinks.HOME)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TOTAL BALANCE", style = TextStyle(color = W.whiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                Text(s.balance, style = TextStyle(color = W.white, fontSize = if (big) 28.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (big) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(s.netWorth, style = TextStyle(color = W.whiteMuted, fontSize = 12.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text("↓ ${s.income}", style = TextStyle(color = ColorProvider(Color(0xFF34E3A0)), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        Spacer(GlanceModifier.width(12.dp))
                        Text("↑ ${s.expense}", style = TextStyle(color = ColorProvider(Color(0xFFFF8FA3)), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}

/* ------------------------------------------------------------------ */
/* 2. Safe-to-Spend                                                    */
/* ------------------------------------------------------------------ */

class SafeToSpendWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 60.dp), DpSize(200.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = prepare(context, id)
        provideContent {
            val s = rememberSnapshot(initial)
            val big = LocalSize.current.height >= 100.dp
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_bg))
                    .padding(16.dp).clickable(openIntent(context, DeepLinks.SAFE)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_widget_sun), contentDescription = null, modifier = GlanceModifier.size(14.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("SAFE TO SPEND TODAY", style = TextStyle(color = W.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                }
                Text(s.safeToday, style = TextStyle(color = W.textPrimary, fontSize = if (big) 28.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (big) {
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = s.safeProgress,
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                        color = W.accent,
                        backgroundColor = W.track,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(s.safeDetail, style = TextStyle(color = W.textSecondary, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}

/* ------------------------------------------------------------------ */
/* 3. Monthly summary                                                  */
/* ------------------------------------------------------------------ */

class MonthlySummaryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 100.dp), DpSize(260.dp, 140.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = prepare(context, id)
        provideContent {
            val s = rememberSnapshot(initial)
            val tall = LocalSize.current.height >= 130.dp
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_bg))
                    .padding(16.dp).clickable(openIntent(context, DeepLinks.INSIGHTS)),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.month.uppercase(), style = TextStyle(color = W.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                    Text(s.updatedLabel, style = TextStyle(color = W.textSecondary, fontSize = 10.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("Income", s.income, W.income, GlanceModifier.defaultWeight())
                    Stat("Expenses", s.expense, W.expense, GlanceModifier.defaultWeight())
                    Stat(s.remainingLabel, s.remaining, W.textPrimary, GlanceModifier.defaultWeight())
                }
                if (tall && s.remainingLabel == "Budget left") {
                    Spacer(GlanceModifier.height(10.dp))
                    LinearProgressIndicator(progress = s.budgetProgress, modifier = GlanceModifier.fillMaxWidth().height(6.dp), color = W.accent, backgroundColor = W.track)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: ColorProvider, modifier: GlanceModifier) {
    Column(modifier) {
        Text(label, style = TextStyle(color = W.textSecondary, fontSize = 11.sp), maxLines = 1)
        Text(value, style = TextStyle(color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

class MonthlySummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthlySummaryWidget()
}

/* ------------------------------------------------------------------ */
/* 4. Quick actions                                                    */
/* ------------------------------------------------------------------ */

class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 60.dp), DpSize(220.dp, 120.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = prepare(context, id)
        provideContent {
            val s = rememberSnapshot(initial)
            val tall = LocalSize.current.height >= 110.dp
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_bg)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tall) {
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).clickable(openIntent(context, DeepLinks.SAFE)), verticalAlignment = Alignment.CenterVertically) {
                        Text("Safe today", style = TextStyle(color = W.textSecondary, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                        Text(s.safeToday, style = TextStyle(color = W.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }
                Row(GlanceModifier.fillMaxWidth()) {
                    ActionTile(context, "Expense", R.drawable.ic_widget_remove, W.expense, DeepLinks.ADD_EXPENSE, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    ActionTile(context, "Income", R.drawable.ic_widget_add, W.income, DeepLinks.ADD_INCOME, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(6.dp))
                    ActionTile(context, "Transfer", R.drawable.ic_widget_swap, W.accent, DeepLinks.ADD_TRANSFER, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun ActionTile(context: Context, label: String, icon: Int, tint: ColorProvider, destination: String, modifier: GlanceModifier) {
    Column(
        modifier.cornerRadius(16.dp).background(W.tileBg).padding(vertical = 8.dp).clickable(openIntent(context, destination)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(22.dp), colorFilter = androidx.glance.ColorFilter.tint(tint))
        Spacer(GlanceModifier.height(2.dp))
        Text(label, style = TextStyle(color = W.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

/* ------------------------------------------------------------------ */
/* Updates                                                             */
/* ------------------------------------------------------------------ */

object WidgetUpdater {
    /** Recomputes the snapshot once and pushes it to every placed widget. */
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val widgets: List<GlanceAppWidget> = listOf(BalanceWidget(), SafeToSpendWidget(), MonthlySummaryWidget(), QuickActionsWidget())
        val placed = widgets.associateWith { w -> runCatching { manager.getGlanceIds(w.javaClass) }.getOrDefault(emptyList()) }
        if (placed.values.all { it.isEmpty() }) return
        val snap = WidgetSnapshot.load(context) ?: return
        placed.forEach { (widget, ids) ->
            ids.forEach { id ->
                runCatching {
                    updateAppWidgetState(context, id) { snap.writeTo(it) }
                    widget.update(context, id)
                }
            }
        }
    }
}

