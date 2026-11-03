package com.equinoxtrack.app

import android.app.Application
import android.content.Context
import androidx.room.InvalidationTracker
import com.equinoxtrack.app.data.db.EquinoxDatabase
import com.equinoxtrack.app.data.io.DataTools
import com.equinoxtrack.app.data.repo.FinanceRepository
import com.equinoxtrack.app.data.settings.SettingsRepository
import com.equinoxtrack.app.domain.AppSettings
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.notifications.Reminders
import com.equinoxtrack.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Manual dependency container: one instance per process. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = EquinoxDatabase.build(context)
    val repository = FinanceRepository(database)
    val settings = SettingsRepository(context)
    val dataTools = DataTools(context, repository, settings)

    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val settingsState: StateFlow<AppSettings?> = settings.settings
        .stateIn(appScope, SharingStarted.Eagerly, null)

    /** Live read model shared by every screen. */
    val ledger: StateFlow<Ledger?> = combine(repository.data, settings.settings, today) { data, s, day ->
        Ledger(data, s, day, ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default)
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One-shot read model for widgets and background work. */
    suspend fun loadLedger(): Ledger =
        Ledger(repository.loadData(), settings.current(), LocalDate.now(), ZoneId.systemDefault())
}

class EquinoxApp : Application() {
    lateinit var container: AppContainer
        private set

    private val dataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)

        container.appScope.launch { container.repository.seedCategoriesIfEmpty() }

        // Keep home-screen widgets in sync with every database or settings change.
        container.database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
            arrayOf("transactions", "accounts", "bills", "bill_payments", "budgets", "goals", "goal_entries", "exchange_rates", "categories"),
        ) {
            override fun onInvalidated(tables: Set<String>) {
                dataChanged.tryEmit(Unit)
            }
        })
        container.appScope.launch {
            merge(dataChanged, container.settings.settings.distinctUntilChanged().drop(1).map { })
                .debounce(700)
                .collect { WidgetUpdater.updateAll(this@EquinoxApp) }
        }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as EquinoxApp).container
