package com.khodiyar.dairybilling.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.admin.AuditScreen
import com.khodiyar.dairybilling.ui.admin.BackupScreen
import com.khodiyar.dairybilling.ui.admin.MoreScreen
import com.khodiyar.dairybilling.ui.admin.SettingsScreen
import com.khodiyar.dairybilling.ui.admin.StaffScreen
import com.khodiyar.dairybilling.ui.auth.LoginScreen
import com.khodiyar.dairybilling.ui.auth.SetupScreen
import com.khodiyar.dairybilling.ui.billing.BillingScreen
import com.khodiyar.dairybilling.ui.billing.DocumentScreen
import com.khodiyar.dairybilling.ui.billing.InvoiceScreen
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appContainer
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.FullScreenLoading
import com.khodiyar.dairybilling.ui.components.resolve
import com.khodiyar.dairybilling.ui.customers.CustomerEditScreen
import com.khodiyar.dairybilling.ui.customers.CustomerListScreen
import com.khodiyar.dairybilling.ui.customers.CustomerProfileScreen
import com.khodiyar.dairybilling.ui.delivery.DailySummaryScreen
import com.khodiyar.dairybilling.ui.delivery.DeliveryScreen
import com.khodiyar.dairybilling.ui.home.DashboardScreen
import com.khodiyar.dairybilling.ui.payments.PaymentEntryScreen
import com.khodiyar.dairybilling.ui.payments.PaymentsScreen
import com.khodiyar.dairybilling.ui.products.ProductDetailScreen
import com.khodiyar.dairybilling.ui.products.ProductEditScreen
import com.khodiyar.dairybilling.ui.products.ProductListScreen
import com.khodiyar.dairybilling.ui.reports.ReportsScreen
import com.khodiyar.dairybilling.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Phase { LOADING, SETUP, LOGIN, MAIN }

class RootViewModel(c: AppContainer) : AppViewModel(c) {
    private val needsSetup = MutableStateFlow<Boolean?>(null)
    val phase = combine(needsSetup, c.session.user) { setup, user ->
        when {
            setup == null -> Phase.LOADING
            user != null -> Phase.MAIN
            setup -> Phase.SETUP
            else -> Phase.LOGIN
        }
    }.stateInVm(Phase.LOADING)

    init {
        viewModelScope.launch {
            val setup = c.auth.needsSetup()
            if (!setup && c.session.current == null) {
                val settings = c.settings.get()
                c.dairyNamed(settings.dairyName) // fills in the dairy list name for installs made before multi-dairy
                if (!c.prefs.features.first().requireSignIn) {
                    // Sign-in switched off (Settings → App features): open straight as the owner, no auto-lock.
                    c.session.autoLockMinutes = 0
                    if (c.auth.signInOwner()) { needsSetup.value = false; return@launch }
                }
                c.session.autoLockMinutes = settings.autoLockMinutes
                c.prefs.readSession()?.let { (userId, lastActive) ->
                    val window = settings.autoLockMinutes * 60_000L
                    if (window > 0 && c.time.nowMillis() - lastActive in 0..window) c.auth.resume(userId)
                }
            }
            needsSetup.value = setup
        }
        // Once someone signs in after setup, the app is no longer in setup mode.
        viewModelScope.launch { c.session.user.collect { if (it != null) needsSetup.value = false } }
    }
}

@Composable
fun AppRoot() {
    val vm = appViewModel { RootViewModel(it) }
    val phase by vm.phase.collectAsState()
    val container = appContainer()
    val snackbar = remember { SnackbarHostState() }
    var lastKind by remember { mutableStateOf(MessageKind.INFO) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        container.messages.collect { m ->
            lastKind = m.kind
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(m.text.resolve(context), duration = if (m.kind == MessageKind.ERROR) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(phase, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "phase") { p ->
            when (p) {
                Phase.LOADING -> FullScreenLoading()
                Phase.SETUP -> SetupScreen()
                Phase.LOGIN -> LoginScreen()
                Phase.MAIN -> MainNav()
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = androidx.compose.ui.unit.Dp(72f))) { data ->
            val (bg, fg, icon) = when (lastKind) {
                MessageKind.SUCCESS -> Triple(AppTheme.ext.successContainer, AppTheme.ext.onSuccessContainer, Icons.Rounded.CheckCircle)
                MessageKind.ERROR -> Triple(AppTheme.ext.dangerContainer, AppTheme.ext.onDangerContainer, Icons.Rounded.ErrorOutline)
                MessageKind.INFO -> Triple(AppTheme.colors.inverseSurface, AppTheme.colors.inverseOnSurface, null)
            }
            Snackbar(containerColor = bg, contentColor = fg, shape = MaterialTheme.shapes.medium) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = fg)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = androidx.compose.ui.unit.Dp(8f)))
                    }
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val container = appContainer()
    val user by container.session.user.collectAsState()
    val canBilling = user?.let { com.khodiyar.dairybilling.security.Access.has(it, Permission.VIEW_BILLING) } == true
    val tabs = listOf(
        Tab(Routes.DASHBOARD, R.string.nav_home, Icons.Rounded.Home),
        Tab(Routes.DELIVERY, R.string.nav_delivery, Icons.Rounded.LocalShipping),
        Tab(Routes.CUSTOMERS, R.string.nav_customers, Icons.Rounded.Groups),
        if (canBilling) Tab(Routes.BILLING, R.string.nav_billing, Icons.AutoMirrored.Rounded.ReceiptLong) else Tab(Routes.PAYMENTS, R.string.nav_payments, Icons.Rounded.Payments),
        Tab(Routes.MORE, R.string.nav_more, Icons.Rounded.MoreHoriz),
    )
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.TOP_LEVEL || (route == Routes.PAYMENTS && !canBilling && nav.previousBackStackEntry == null)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) NavigationBar(containerColor = AppTheme.colors.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = AppTheme.colors.primaryContainer, selectedIconColor = AppTheme.colors.onPrimaryContainer),
                    )
                }
            }
        },
    ) { inner ->
        val base = Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)
        NavGraph(nav, if (showBar) base.imePadding() else base.navigationBarsPadding().imePadding())
    }
}

@Composable
private fun NavGraph(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val c = appContainer()
    fun allowed(p: Permission) = c.session.has(p)
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }
    NavHost(nav, startDestination = Routes.DASHBOARD, modifier = modifier) {
        composable(Routes.DASHBOARD) { DashboardScreen(go) }
        composable(Routes.DELIVERY) { DeliveryScreen(go) }
        composable(Routes.DAILY_SUMMARY, listOf(longArg("date"))) { DailySummaryScreen(it.arguments!!.getLong("date"), back, go) }
        composable(Routes.CUSTOMERS) { CustomerListScreen(go) }
        composable(Routes.CUSTOMER, listOf(longArg("id"))) { CustomerProfileScreen(it.arguments!!.getLong("id"), back, go) }
        composable(Routes.CUSTOMER_EDIT, listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            CustomerEditScreen(id, back) { saved -> nav.popBackStack(); if (id == 0L) go(Routes.customer(saved)) }
        }
        composable(Routes.PRODUCTS) { ProductListScreen(back, go) }
        composable(Routes.PRODUCT, listOf(longArg("id"))) { ProductDetailScreen(it.arguments!!.getLong("id"), back, go) }
        composable(Routes.PRODUCT_EDIT, listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            ProductEditScreen(id, back) { saved -> nav.popBackStack(); if (id == 0L) go(Routes.product(saved)) }
        }
        composable(Routes.PAYMENTS) { PaymentsScreen(if (nav.previousBackStackEntry != null) back else null, go) }
        composable(Routes.PAYMENT_NEW, listOf(longArg("customerId"))) { PaymentEntryScreen(it.arguments!!.getLong("customerId"), back, go) }
        composable(Routes.BILLING) { if (allowed(Permission.VIEW_BILLING)) BillingScreen(go) else Denied(back) }
        composable(Routes.INVOICE, listOf(longArg("customerId"), navArgument("period") { type = NavType.IntType })) {
            if (allowed(Permission.VIEW_BILLING)) InvoiceScreen(it.arguments!!.getLong("customerId"), it.arguments!!.getInt("period"), back, go) else Denied(back)
        }
        composable(Routes.DOCUMENT, listOf(navArgument("kind") { type = NavType.StringType }, longArg("a"), longArg("b"), longArg("c"))) {
            val a = it.arguments!!
            DocumentScreen(a.getString("kind") ?: "invoice", a.getLong("a"), a.getLong("b"), a.getLong("c"), back)
        }
        composable(Routes.REPORTS) { if (allowed(Permission.VIEW_REPORTS)) ReportsScreen(back) else Denied(back) }
        composable(Routes.BACKUP) { if (allowed(Permission.BACKUP)) BackupScreen(back) {} else Denied(back) }
        composable(Routes.STAFF) { if (allowed(Permission.MANAGE_USERS)) StaffScreen(back) else Denied(back) }
        composable(Routes.SETTINGS) { SettingsScreen(back) }
        composable(Routes.AUDIT) { if (allowed(Permission.VIEW_AUDIT)) AuditScreen(back) else Denied(back) }
        composable(Routes.MORE) { MoreScreen(go) }
    }
}

@Composable
private fun Denied(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column {
        com.khodiyar.dairybilling.ui.components.AppTopBar(stringResource(R.string.err_not_permitted), onBack = onBack)
        com.khodiyar.dairybilling.ui.components.EmptyState(Icons.Rounded.ErrorOutline, stringResource(R.string.err_not_permitted), stringResource(R.string.msg_ask_admin))
    }
}
