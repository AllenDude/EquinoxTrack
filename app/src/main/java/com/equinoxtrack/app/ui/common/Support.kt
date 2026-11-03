package com.equinoxtrack.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.equinoxtrack.app.AppContainer
import com.equinoxtrack.app.appContainer
import com.equinoxtrack.app.data.db.TransactionWithLines
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Money
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.components.AppIcons
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.theme.AmountStyle
import com.equinoxtrack.app.ui.theme.Eq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/* ------------------------------------------------------------------ */
/* ViewModel plumbing                                                  */
/* ------------------------------------------------------------------ */

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Common base: access to the container, the live ledger and a one-shot message channel. */
abstract class BaseViewModel(protected val container: AppContainer) : ViewModel() {
    val ledger = container.ledger
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a save/delete completed and the screen should close. */
    val done: SharedFlow<Unit> = _done

    protected fun message(text: String) { _messages.tryEmit(text) }
    protected fun finish() { _done.tryEmit(Unit) }

    /** Runs a write, reporting validation errors as messages instead of crashing. */
    protected fun act(success: String? = null, close: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { message(it) }
                if (close) finish()
            } catch (e: IllegalArgumentException) {
                message(e.message ?: "Please check the details")
            } catch (e: IllegalStateException) {
                message(e.message ?: "Something went wrong")
            }
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** App-lifetime scope so a "Saved" snackbar survives the editor screen closing. */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope?> { null }

/** Shows VM messages as snackbars and closes the screen on [BaseViewModel.done]. */
@Composable
fun VmEffects(vm: BaseViewModel, onDone: (() -> Unit)? = null) {
    val snackbar = LocalSnackbar.current
    val scope = LocalAppScope.current ?: rememberCoroutineScope()
    LaunchedEffect(vm) { vm.messages.collect { msg -> scope.launch { snackbar.showSnackbar(msg) } } }
    if (onDone != null) LaunchedEffect(vm) { vm.done.collect { onDone() } }
}

/* ------------------------------------------------------------------ */
/* Formatting                                                          */
/* ------------------------------------------------------------------ */

object Fmt {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val dayYearFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val shortFmt = DateTimeFormatter.ofPattern("MMM d")
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    fun dayHeader(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayFmt) else date.format(dayYearFmt)
    }

    fun short(date: LocalDate): String = date.format(shortFmt)
    fun full(date: LocalDate): String = date.format(dayYearFmt)
    fun month(date: LocalDate): String = date.format(monthFmt)
    fun time(t: java.time.LocalTime): String = t.format(timeFmt)

    fun dueLabel(daysUntil: Long): String = when {
        daysUntil < -1 -> "${-daysUntil} days overdue"
        daysUntil == -1L -> "1 day overdue"
        daysUntil == 0L -> "Due today"
        daysUntil == 1L -> "Due tomorrow"
        else -> "Due in $daysUntil days"
    }

    fun percent(f: Float): String = "${(f * 100).toInt()}%"
}

/* ------------------------------------------------------------------ */
/* Shared transaction row                                              */
/* ------------------------------------------------------------------ */

data class TxVisual(val title: String, val subtitle: String, val iconKey: String?, val tint: Long?, val signedAmount: Long, val currency: String, val type: TxType, val split: Boolean)

fun Ledger.visual(t: TransactionWithLines, perspectiveAccount: Long? = null): TxVisual {
    val tx = t.tx
    val first = t.lines.firstOrNull()?.categoryId?.let { categoriesById[it] }
    val signed = when (tx.type) {
        TxType.INCOME -> tx.amountMinor
        TxType.EXPENSE -> -tx.amountMinor
        TxType.ADJUSTMENT -> tx.amountMinor
        TxType.TRANSFER -> when (perspectiveAccount) {
            null -> tx.amountMinor
            tx.toAccountId -> tx.toAmountMinor ?: tx.amountMinor
            else -> -tx.amountMinor
        }
    }
    val currency = if (tx.type == TxType.TRANSFER && perspectiveAccount != null && perspectiveAccount == tx.toAccountId) currencyOf(perspectiveAccount) else currencyOf(tx.accountId)
    return TxVisual(
        title = titleOf(t),
        subtitle = subtitleOf(t),
        iconKey = if (t.lines.size > 1) null else first?.icon,
        tint = first?.color,
        signedAmount = signed,
        currency = currency,
        type = tx.type,
        split = t.lines.size > 1,
    )
}

@Composable
fun TransactionRow(v: TxVisual, onClick: () -> Unit, hidden: Boolean = false, trailingNote: String? = null) {
    val c = Eq.colors
    val icon = when {
        v.type == TxType.TRANSFER -> Icons.Rounded.SwapHoriz
        v.type == TxType.ADJUSTMENT -> Icons.Rounded.Tune
        v.split -> Icons.Rounded.CallSplit
        else -> AppIcons.of(v.iconKey)
    }
    val tint = when (v.type) {
        TxType.TRANSFER -> c.transfer
        TxType.ADJUSTMENT -> c.textSecondary
        else -> v.tint?.let { Color(it) } ?: c.accent
    }
    val amountColor = when (v.type) {
        TxType.INCOME -> c.income
        TxType.EXPENSE -> c.textPrimary
        TxType.TRANSFER -> c.transfer
        TxType.ADJUSTMENT -> c.textSecondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = Eq.type.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(v.subtitle, style = Eq.type.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) Money.masked(v.currency) else Money.format(v.signedAmount, v.currency, signed = v.type == TxType.INCOME || (v.type == TxType.TRANSFER && v.signedAmount > 0)),
                style = AmountStyle,
                color = amountColor,
                maxLines = 1,
            )
            if (trailingNote != null) Text(trailingNote, style = Eq.type.labelSmall, color = c.textTertiary)
        }
    }
}

/** Grouped card wrapper for lists. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Eq.colors.surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}
