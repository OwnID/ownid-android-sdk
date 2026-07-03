package com.ownid.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ownid.demo.api.ApiDiscoverLoginScreen
import com.ownid.demo.api.ApiOidcScreen
import com.ownid.demo.api.ApiPasskeyAssertionsScreen
import com.ownid.demo.api.ApiPasskeyCreateEnrollScreen
import com.ownid.demo.api.ApiVerificationEnrollScreen
import com.ownid.demo.api.ApiViewModel
import com.ownid.demo.flow.boost.BoostCreatePasskeyScreen
import com.ownid.demo.flow.boost.BoostLoginScreen
import com.ownid.demo.flow.elite.EliteFlowScreen
import com.ownid.demo.headless.HeadlessScreen
import com.ownid.demo.ops.OperationDiscoverLoginScreen
import com.ownid.demo.ops.OperationLoginIdCollectScreen
import com.ownid.demo.ops.OperationPasskeyAssertionsScreen
import com.ownid.demo.ops.OperationPasskeyCreateEnrollScreen
import com.ownid.demo.ops.OperationSignInWithGoogleScreen
import com.ownid.demo.ops.OperationVerificationScreen
import com.ownid.demo.ops.OperationViewModel
import com.ownid.demo.ui.DemoAppTheme
import com.ownid.demo.user.CurrentUserScreen
import com.ownid.sdk.compose.OwnIdThemeBridge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DemoAppTheme {
                OwnIdThemeBridge()

                val authIntegration = DemoAuthIntegrationProvider.integration
                val isSignedIn = authIntegration.isSignedIn.collectAsStateWithLifecycle(initialValue = false).value
                val initialRoute: NavKey = if (isSignedIn) DemoRoute.CurrentUser else DemoRoute.ApiRoot
                val backStack = rememberNavBackStack(initialRoute)
                val currentRoute = (backStack.lastOrNull() as? DemoRoute) ?: DemoRoute.ApiRoot

                LaunchedEffect(isSignedIn, currentRoute) {
                    if (isSignedIn != (currentRoute == DemoRoute.CurrentUser)) {
                        backStack.clear()
                        backStack.add(if (isSignedIn) DemoRoute.CurrentUser else DemoRoute.ApiRoot)
                    }
                }

                DemoNavigationSuite(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.root) {
                            backStack.clear()
                            backStack.add(tab.root)
                        }
                    }
                ) {
                    DemoScaffold(
                        currentRoute = currentRoute,
                        onBackClick = { backStack.removeLastOrNull() }
                    ) { innerPadding ->
                        DemoNavDisplay(
                            backStack = backStack,
                            authIntegration = authIntegration,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DemoNavigationSuite(
        currentRoute: DemoRoute,
        onTabSelected: (Tab) -> Unit,
        content: @Composable () -> Unit
    ) {
        if (currentRoute == DemoRoute.CurrentUser) {
            content()
        } else {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    Tab.entries.forEach { tab ->
                        item(
                            selected = currentRoute.tab == tab,
                            onClick = { onTabSelected(tab) },
                            icon = { Icon(painter = painterResource(tab.iconRes), contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            ) {
                content()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DemoScaffold(
        currentRoute: DemoRoute,
        onBackClick: () -> Unit,
        content: @Composable (PaddingValues) -> Unit
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentRoute.topBarTitle,
                            maxLines = if (currentRoute.isTopLevel) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            style = if (currentRoute.isTopLevel) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleSmall
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentRoute.canNavigateBack) {
                            IconButton(onClick = onBackClick) {
                                Icon(painter = painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .height(32.dp),
                            colorFilter = ColorFilter.tint(LocalContentColor.current)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            content = content
        )
    }

    @Composable
    private fun DemoNavDisplay(
        backStack: NavBackStack<NavKey>,
        authIntegration: DemoAuthIntegration,
        modifier: Modifier = Modifier
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else finish() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                rememberTabViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry(DemoRoute.CurrentUser, contentKey = DemoRoute.CurrentUser.name) {
                    ConstrainedContent {
                        CurrentUserScreen(
                            loadCurrentUser = authIntegration::loadCurrentUser,
                            onLogout = authIntegration::signOut,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Tab.entries.forEach { tab ->
                    entry(tab.root, contentKey = tab.root.name, metadata = tab.root.tabViewModelStoreMetadata()) {
                        if (tab == Tab.Headless) {
                            ConstrainedContent {
                                HeadlessScreen()
                            }
                        } else {
                            RouteListScreen(tab = tab, onRouteClick = { route -> backStack.add(route) })
                        }
                    }

                    tab.screens.forEach { route ->
                        entry(key = route, contentKey = route.name, metadata = route.tabViewModelStoreMetadata()) {
                            ConstrainedContent {
                                when (route) {
                                    DemoRoute.ApiVerificationEnroll -> ApiVerificationEnrollScreen(viewModel = viewModel<ApiViewModel>())
                                    DemoRoute.ApiPasskeyCreateEnroll -> ApiPasskeyCreateEnrollScreen(viewModel = viewModel<ApiViewModel>())
                                    DemoRoute.ApiPasskeyAssertions -> ApiPasskeyAssertionsScreen(viewModel = viewModel<ApiViewModel>())
                                    DemoRoute.ApiDiscoverLogin -> ApiDiscoverLoginScreen(viewModel = viewModel<ApiViewModel>())
                                    DemoRoute.ApiOidc -> ApiOidcScreen(viewModel = viewModel<ApiViewModel>())
                                    DemoRoute.OperationsLoginIdCollect -> OperationLoginIdCollectScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.OperationsVerification -> OperationVerificationScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.OperationsPasskeyCreateEnroll -> OperationPasskeyCreateEnrollScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.OperationsPasskeyAssertions -> OperationPasskeyAssertionsScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.OperationsDiscoverLogin -> OperationDiscoverLoginScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.OperationsSignInWithGoogle -> OperationSignInWithGoogleScreen(viewModel = viewModel<OperationViewModel>())
                                    DemoRoute.FlowsBoostCreatePasskey -> BoostCreatePasskeyScreen(onRegister = authIntegration::registerAndSignIn)
                                    DemoRoute.FlowsBoostLogin -> BoostLoginScreen(onPasswordLogin = authIntegration::signIn)
                                    DemoRoute.FlowsElite -> EliteFlowScreen()
                                    DemoRoute.CurrentUser, DemoRoute.ApiRoot, DemoRoute.OperationsRoot, DemoRoute.FlowsRoot, DemoRoute.HeadlessRoot -> Unit
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun ConstrainedContent(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxHeight()
        ) {
            content()
        }
    }

    @Composable
    private fun RouteListScreen(tab: Tab, onRouteClick: (DemoRoute) -> Unit) {
        ConstrainedContent {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = tab.screens, key = { it.name }) { route ->
                    OutlinedCard(onClick = { onRouteClick(route) }) {
                        ListItem(
                            headlineContent = { Text(text = route.title) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.chevron_right_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun rememberTabViewModelStoreNavEntryDecorator(): NavEntryDecorator<NavKey> {
        // TODO: When demoAdvanced moves to API 37 and Android Gradle Plugin 9.0, consider replacing
        // this with Lifecycle 2.11's rememberViewModelStoreOwner and separate API/Operations tab
        // composables so tab-scoped ViewModel ownership can be expressed with standard lifecycle APIs.
        val stores = retain { mutableMapOf<String, ViewModelStore>() }

        RetainedEffect(stores) {
            onRetire {
                stores.values.forEach { it.clear() }
                stores.clear()
            }
        }

        return remember(stores) {
            NavEntryDecorator(
                onPop = { contentKey -> (contentKey as? String)?.takeIf(stores::containsKey)?.let { stores.remove(it)?.clear() } },
                decorate = { entry ->
                    val tabKey = entry.metadata[TabViewModelStoreKey]

                    if (tabKey == null) {
                        entry.Content()
                    } else {
                        val store = stores.getOrPut(tabKey) { ViewModelStore() }
                        val owner = remember(store) {
                            object : ViewModelStoreOwner {
                                override val viewModelStore: ViewModelStore = store
                            }
                        }

                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                            entry.Content()
                        }
                    }
                }
            )
        }
    }

    private object TabViewModelStoreKey : NavMetadataKey<String>

    private enum class Tab(val label: String, val title: String, val iconRes: Int) {
        Api("APIs", "APIs", R.drawable.data_object_24px),
        Operations("OPs", "Operations", R.drawable.play_arrow_24px),
        Flows("Flows", "Flows", R.drawable.account_tree_24px),
        Headless("Headless", "Headless", R.drawable.dns_24px);

        val root: DemoRoute
            get() = DemoRoute.entries.first { route -> route.tab == this && route.isRoot }

        val screens: List<DemoRoute>
            get() = DemoRoute.entries.filter { route -> route.tab == this && !route.isRoot }
    }

    private enum class DemoRoute(val title: String, val tab: Tab? = null, val isRoot: Boolean = false) : NavKey {
        CurrentUser("Current User"),

        ApiRoot("APIs", Tab.Api, isRoot = true),
        ApiVerificationEnroll("Verification and Enroll", Tab.Api),
        ApiPasskeyCreateEnroll("Passkey Create and Enroll", Tab.Api),
        ApiPasskeyAssertions("Passkey Assertions", Tab.Api),
        ApiDiscoverLogin("Discover and Login", Tab.Api),
        ApiOidc("OIDC", Tab.Api),

        OperationsRoot("Operations", Tab.Operations, isRoot = true),
        OperationsLoginIdCollect("Login ID Collect", Tab.Operations),
        OperationsVerification("Verification", Tab.Operations),
        OperationsPasskeyCreateEnroll("Passkey Create and Enroll", Tab.Operations),
        OperationsPasskeyAssertions("Passkey Assertions", Tab.Operations),
        OperationsDiscoverLogin("Discover and Login", Tab.Operations),
        OperationsSignInWithGoogle("Sign In with Google", Tab.Operations),

        FlowsRoot("Flows", Tab.Flows, isRoot = true),
        FlowsBoostCreatePasskey("Boost Create Passkey", Tab.Flows),
        FlowsBoostLogin("Boost Login", Tab.Flows),
        FlowsElite("Elite", Tab.Flows),

        HeadlessRoot("Passkey Enroll", Tab.Headless, isRoot = true);

        val isTopLevel: Boolean
            get() = this == CurrentUser || isRoot

        val canNavigateBack: Boolean
            get() = tab != null && !isRoot

        val topBarTitle: String
            get() = when {
                this == CurrentUser -> title
                isRoot -> tab!!.title
                else -> "${tab!!.title}\n$title"
            }

        fun tabViewModelStoreMetadata(): Map<String, Any> = when {
            tab != null -> metadata { put(TabViewModelStoreKey, tab.root.name) }
            else -> emptyMap()
        }
    }
}
