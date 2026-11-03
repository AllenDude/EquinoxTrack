package com.equinoxtrack.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.equinoxtrack.app.domain.AppSettings
import com.equinoxtrack.app.domain.TxType
import com.equinoxtrack.app.ui.activity.ActivityScreen
import com.equinoxtrack.app.ui.activity.TransactionEditorScreen
import com.equinoxtrack.app.ui.common.LocalAppScope
import com.equinoxtrack.app.ui.common.LocalSnackbar
import com.equinoxtrack.app.ui.components.IconBadge
import com.equinoxtrack.app.ui.components.ScreenBackground
import com.equinoxtrack.app.ui.components.pressable
import com.equinoxtrack.app.ui.home.HomeScreen
import com.equinoxtrack.app.ui.insights.InsightsScreen
import com.equinoxtrack.app.ui.plan.BillDetailScreen
import com.equinoxtrack.app.ui.plan.BillEditScreen
import com.equinoxtrack.app.ui.plan.BudgetEditScreen
import com.equinoxtrack.app.ui.plan.GoalDetailScreen
import com.equinoxtrack.app.ui.plan.GoalEditScreen
import com.equinoxtrack.app.ui.plan.PlanScreen
import com.equinoxtrack.app.ui.plan.SafeToSpendScreen
import com.equinoxtrack.app.ui.settings.CategoriesScreen
import com.equinoxtrack.app.ui.settings.DataScreen
import com.equinoxtrack.app.ui.settings.LockScreen
import com.equinoxtrack.app.ui.settings.OnboardingScreen
import com.equinoxtrack.app.ui.settings.RatesScreen
import com.equinoxtrack.app.ui.settings.SettingsScreen
import com.equinoxtrack.app.ui.theme.Eq
import com.equinoxtrack.app.ui.theme.EquinoxTheme
import com.equinoxtrack.app.ui.wallet.AccountDetailScreen
import com.equinoxtrack.app.ui.wallet.AccountEditScreen
import com.equinoxtrack.app.ui.wallet.AssetEditScreen
import com.equinoxtrack.app.ui.wallet.WalletScreen

object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val PLAN = "plan?tab={tab}"
    const val INSIGHTS = "insights"
    const val WALLET = "wallet"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val DATA = "data"
    const val RATES = "rates"
    const val SAFE = "safe"
    const val TX = "tx?id={id}&type={type}&account={account}"
    const val ACCOUNT = "account/{id}"
    const val ACCOUNT_EDIT = "accountEdit?id={id}"
    const val ASSET_EDIT = "assetEdit?id={id}"
    const val BUDGET_EDIT = "budgetEdit?id={id}"
    const val BILL = "bill/{id}"
    const val BILL_EDIT = "billEdit?id={id}"
    const val GOAL = "goal/{id}"
    const val GOAL_EDIT = "goalEdit?id={id}"

    fun plan(tab: Int) = "plan?tab=$tab"
    fun tx(id: Long = 0, type: TxType = TxType.EXPENSE, account: Long = 0) = "tx?id=$id&type=${type.name}&account=$account"
    fun account(id: Long) = "account/$id"
    fun accountEdit(id: Long) = "accountEdit?id=$id"
    fun assetEdit(id: Long) = "assetEdit?id=$id"
    fun budgetEdit(id: Long) = "budgetEdit?id=$id"
    fun bill(id: Long) = "bill/$id"
    fun billEdit(id: Long) = "billEdit?id=$id"
    fun goal(id: Long) = "goal/$id"
    fun goalEdit(id: Long) = "goalEdit?id=$id"
}

/** Destinations that widgets and shortcuts can open. */
object DeepLinks {
    const val EXTRA = "eqx_destination"
    const val ADD_EXPENSE = "add_expense"
    const val ADD_INCOME = "add_income"
    const val ADD_TRANSFER = "add_transfer"
    const val SAFE = "safe"
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val BUDGETS = "budgets"
    const val BILLS = "bills"
    const val WALLET = "wallet"
}

private data class Tab(val route: String, val base: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "home", "Home", Icons.Rounded.Home),
    Tab(Routes.ACTIVITY, "activity", "Activity", Icons.Rounded.ReceiptLong),
    Tab(Routes.plan(0), "plan", "Plan", Icons.Rounded.DonutLarge),
    Tab(Routes.INSIGHTS, "insights", "Insights", Icons.Rounded.Insights),
    Tab(Routes.WALLET, "wallet", "Wallet", Icons.Rounded.AccountBalanceWallet),
)

@Composable
fun AppRoot(
    settings: AppSettings?,
    locked: Boolean,
    onUnlock: () -> Unit,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
) {
    val s = settings ?: AppSettings()
    EquinoxTheme(themeMode = s.themeMode, accent = s.accent) {
        val snackbar = remember { SnackbarHostState() }
        val appScope = rememberCoroutineScope()
        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalAppScope provides appScope) {
            ScreenBackground {
                when {
                    settings == null -> Unit
                    locked -> LockScreen(onUnlock)
                    !settings.onboardingDone -> OnboardingScreen()
                    else -> MainNavigation(deepLink, onDeepLinkHandled)
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavigation(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: Routes.HOME
    val isTopLevel = tabs.any { currentRoute.substringBefore('?') == it.base }
    val showFab = currentRoute == Routes.HOME || currentRoute == Routes.ACTIVITY || currentRoute == Routes.WALLET
    var quickAdd by remember { mutableStateOf(false) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = navInset + 92.dp)

    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            DeepLinks.ADD_EXPENSE -> nav.navigate(Routes.tx(type = TxType.EXPENSE))
            DeepLinks.ADD_INCOME -> nav.navigate(Routes.tx(type = TxType.INCOME))
            DeepLinks.ADD_TRANSFER -> nav.navigate(Routes.tx(type = TxType.TRANSFER))
            DeepLinks.SAFE -> nav.navigate(Routes.SAFE)
            DeepLinks.INSIGHTS -> nav.goTab(Routes.INSIGHTS)
            DeepLinks.BUDGETS -> nav.goTab(Routes.plan(0))
            DeepLinks.BILLS -> nav.navigate(Routes.plan(1)) { launchSingleTop = true }
            DeepLinks.WALLET -> nav.goTab(Routes.WALLET)
            else -> nav.goTab(Routes.HOME)
        }
        onDeepLinkHandled()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 14 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 14 } },
        ) {
            val idArg = navArgument("id") { type = NavType.LongType; defaultValue = 0L }

            composable(Routes.HOME) {
                HomeScreen(
                    onAdd = { nav.navigate(Routes.tx(type = it)) },
                    onOpenTransaction = { nav.navigate(Routes.tx(id = it)) },
                    onSeeActivity = { nav.goTab(Routes.ACTIVITY) },
                    onOpenWallet = { nav.goTab(Routes.WALLET) },
                    onOpenSafe = { nav.navigate(Routes.SAFE) },
                    onOpenInsights = { nav.goTab(Routes.INSIGHTS) },
                    onOpenBudgets = { nav.navigate(Routes.plan(0)) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true } },
                    onOpenBills = { nav.navigate(Routes.plan(1)) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true } },
                    onOpenBill = { nav.navigate(Routes.bill(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenRates = { nav.navigate(Routes.RATES) },
                    bottomPadding = contentPadding,
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(onOpenTransaction = { nav.navigate(Routes.tx(id = it)) }, bottomPadding = contentPadding)
            }
            composable(Routes.PLAN, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { e ->
                PlanScreen(
                    initialTab = e.arguments?.getInt("tab") ?: 0,
                    bottomPadding = contentPadding,
                    onEditBudget = { nav.navigate(Routes.budgetEdit(it)) },
                    onOpenBill = { nav.navigate(Routes.bill(it)) },
                    onEditBill = { nav.navigate(Routes.billEdit(it)) },
                    onOpenGoal = { nav.navigate(Routes.goal(it)) },
                    onEditGoal = { nav.navigate(Routes.goalEdit(it)) },
                    onOpenSafe = { nav.navigate(Routes.SAFE) },
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen(contentPadding) }
            composable(Routes.WALLET) {
                WalletScreen(
                    onOpenAccount = { nav.navigate(Routes.account(it)) },
                    onAddAccount = { nav.navigate(Routes.accountEdit(0)) },
                    onOpenAsset = { nav.navigate(Routes.assetEdit(it)) },
                    onAddAsset = { nav.navigate(Routes.assetEdit(0)) },
                    bottomPadding = contentPadding,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onData = { nav.navigate(Routes.DATA) },
                    onRates = { nav.navigate(Routes.RATES) },
                    onSafe = { nav.navigate(Routes.SAFE) },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DATA) { DataScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RATES) { RatesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SAFE) {
                SafeToSpendScreen(
                    onBack = { nav.popBackStack() },
                    onOpenBill = { nav.navigate(Routes.bill(it)) },
                    onOpenBudget = { nav.navigate(Routes.budgetEdit(it)) },
                )
            }
            composable(
                Routes.TX,
                arguments = listOf(
                    idArg,
                    navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                    navArgument("account") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                val args = e.arguments
                TransactionEditorScreen(
                    editId = args?.getLong("id") ?: 0L,
                    initialType = runCatching { TxType.valueOf(args?.getString("type") ?: "EXPENSE") }.getOrDefault(TxType.EXPENSE),
                    initialAccount = args?.getLong("account") ?: 0L,
                    onClose = { nav.popBackStack() },
                    onCreateAccount = { nav.navigate(Routes.accountEdit(0)) },
                )
            }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                AccountDetailScreen(
                    accountId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.accountEdit(id)) },
                    onOpenTransaction = { nav.navigate(Routes.tx(id = it)) },
                    onAdd = { nav.navigate(Routes.tx(type = it, account = id)) },
                )
            }
            composable(Routes.ACCOUNT_EDIT, arguments = listOf(idArg)) { e ->
                AccountEditScreen(
                    accountId = e.arguments?.getLong("id") ?: 0L,
                    onClose = { nav.popBackStack() },
                    onDeleted = { nav.popBackStack(Routes.WALLET, inclusive = false) || nav.popBackStack() },
                )
            }
            composable(Routes.ASSET_EDIT, arguments = listOf(idArg)) { e ->
                AssetEditScreen(assetId = e.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() })
            }
            composable(Routes.BUDGET_EDIT, arguments = listOf(idArg)) { e ->
                BudgetEditScreen(budgetId = e.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() })
            }
            composable(Routes.BILL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                BillDetailScreen(
                    billId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.billEdit(id)) },
                    onOpenTransaction = { nav.navigate(Routes.tx(id = it)) },
                )
            }
            composable(Routes.BILL_EDIT, arguments = listOf(idArg)) { e ->
                BillEditScreen(billId = e.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() })
            }
            composable(Routes.GOAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                GoalDetailScreen(goalId = id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.goalEdit(id)) })
            }
            composable(Routes.GOAL_EDIT, arguments = listOf(idArg)) { e ->
                GoalEditScreen(
                    goalId = e.arguments?.getLong("id") ?: 0L,
                    onClose = { nav.popBackStack() },
                    onDeleted = { nav.popBackStack(Routes.PLAN, inclusive = false) || nav.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomBar(currentRoute) { nav.goTab(it) }
        }
        AnimatedVisibility(
            visible = showFab,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = 96.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            AddButton { quickAdd = true }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onPick = { action ->
                quickAdd = false
                when (action) {
                    "expense" -> nav.navigate(Routes.tx(type = TxType.EXPENSE))
                    "income" -> nav.navigate(Routes.tx(type = TxType.INCOME))
                    "transfer" -> nav.navigate(Routes.tx(type = TxType.TRANSFER))
                    "bill" -> nav.navigate(Routes.plan(1)) { launchSingleTop = true }
                    "goal" -> nav.navigate(Routes.plan(2)) { launchSingleTop = true }
                }
            },
        )
    }
}

@Composable
private fun BottomBar(currentRoute: String, onSelect: (String) -> Unit) {
    val c = Eq.colors
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .background(c.surfaceRaised.copy(alpha = 0.97f), shape)
            .border(1.dp, c.stroke, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute.substringBefore('?') == tab.base
            val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, label = "tabBg")
            val fg by animateColorAsState(if (selected) c.accent else c.textTertiary, label = "tabFg")
            Column(
                Modifier
                    .weight(1f)
                    .pressable({ onSelect(tab.route) }, RoundedCornerShape(22.dp))
                    .background(bg, RoundedCornerShape(22.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(23.dp))
                Spacer(Modifier.height(2.dp))
                Text(tab.label, style = Eq.type.labelSmall, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val c = Eq.colors
    Box(
        Modifier
            .size(62.dp)
            .shadow(18.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
            .pressable(onClick, CircleShape)
            .background(c.accentBrush, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Add transaction", tint = c.onAccent, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Eq.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Quick add", style = Eq.type.headlineSmall, color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Expense", Icons.Rounded.Remove, c.expense, Modifier.weight(1f)) { onPick("expense") }
                QuickTile("Income", Icons.Rounded.Add, c.income, Modifier.weight(1f)) { onPick("income") }
                QuickTile("Transfer", Icons.Rounded.SwapHoriz, c.transfer, Modifier.weight(1f)) { onPick("transfer") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Pay a bill", Icons.Rounded.Receipt, c.warning, Modifier.weight(1f)) { onPick("bill") }
                QuickTile("Save to goal", Icons.Rounded.Savings, c.accent, Modifier.weight(1f)) { onPick("goal") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Eq.colors
    Column(
        modifier
            .pressable(onClick, RoundedCornerShape(22.dp))
            .background(c.surfaceRaised, RoundedCornerShape(22.dp))
            .border(1.dp, c.stroke, RoundedCornerShape(22.dp))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, tint, size = 46.dp, corner = 16.dp)
        Spacer(Modifier.height(10.dp))
        Text(label, style = Eq.type.labelLarge, color = c.textPrimary)
    }
}

