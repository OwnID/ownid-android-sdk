package com.ownid.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.ownid.demo.boost.BoostFlowScreen
import com.ownid.demo.elite.EliteFlowScreen
import com.ownid.demo.headless.HeadlessScreen
import com.ownid.demo.ui.DemoAppTheme
import com.ownid.demo.user.CurrentUserScreen
import com.ownid.sdk.compose.OwnIdThemeBridge
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    @Serializable
    private enum class DemoRoute(val title: String, val showOnHome: Boolean = true) : NavKey {
        Home("OwnID Demo - ${BuildConfig.APPLICATION_ID.substringAfterLast('.').uppercase()}", showOnHome = false),
        Boost("Boost Flow"),
        Elite("Elite Flow"),
        Headless("Headless"),
        CurrentUser("Current User", showOnHome = false);

        val canNavigateBack: Boolean
            get() = this != Home && this != CurrentUser

        companion object {
            val homeRoutes: List<DemoRoute> = entries.filter { route -> route.showOnHome }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DemoAppTheme {
                OwnIdThemeBridge()

                val initialSession = DemoIdentityPlatform.currentSession()
                val initialRoute: DemoRoute = if (initialSession != null) DemoRoute.CurrentUser else DemoRoute.Home
                val backStack = rememberSerializable(serializer = NavBackStackSerializer(DemoRoute.serializer())) {
                    NavBackStack(initialRoute)
                }
                val sessionFlow = remember { DemoIdentityPlatform.observeSession() }
                val currentSession = sessionFlow.collectAsStateWithLifecycle(initialValue = initialSession).value
                val currentRoute = backStack.lastOrNull() ?: initialRoute

                LaunchedEffect(currentSession, currentRoute) {
                    val isLoggedIn = currentSession != null
                    val isOnCurrentUserRoute = currentRoute == DemoRoute.CurrentUser
                    if (isLoggedIn != isOnCurrentUserRoute) {
                        val target = if (isLoggedIn) DemoRoute.CurrentUser else DemoRoute.Home
                        backStack.clear()
                        backStack.add(target)
                    }
                }

                DemoScaffold(
                    currentRoute = currentRoute,
                    onBackClick = { backStack.removeLastOrNull() }
                ) { innerPadding ->
                    DemoNavDisplay(
                        backStack = backStack,
                        currentSession = currentSession,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
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
                            text = currentRoute.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
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
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
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
        backStack: NavBackStack<DemoRoute>,
        currentSession: DemoUserSession?,
        modifier: Modifier = Modifier
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else this@MainActivity.finish() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry(DemoRoute.Home) {
                    DemoDestinationSelector(
                        onDestinationClick = { route ->
                            if (backStack.lastOrNull() != DemoRoute.Home) return@DemoDestinationSelector
                            if (route !in backStack) backStack.add(route)
                        }
                    )
                }

                entry(DemoRoute.Boost) {
                    ConstrainedContent {
                        BoostFlowScreen(
                            onPasswordLogin = DemoIdentityPlatform::passwordLogin,
                            onRegister = DemoIdentityPlatform::registerAndSaveSession,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                entry(DemoRoute.Elite) {
                    ConstrainedContent {
                        EliteFlowScreen(modifier = Modifier.fillMaxSize())
                    }
                }

                entry(DemoRoute.Headless) {
                    ConstrainedContent {
                        HeadlessScreen(modifier = Modifier.fillMaxSize())
                    }
                }

                entry(DemoRoute.CurrentUser) {
                    ConstrainedContent {
                        currentSession?.let { session ->
                            CurrentUserScreen(
                                session = session,
                                loadCurrentUser = DemoIdentityPlatform::loadCurrentUser,
                                onLogout = DemoIdentityPlatform::logout,
                                modifier = Modifier.fillMaxSize()
                            )
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
    private fun DemoDestinationSelector(
        onDestinationClick: (DemoRoute) -> Unit
    ) {
        ConstrainedContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DemoRoute.homeRoutes.forEach { route ->
                    OutlinedCard(
                        onClick = { onDestinationClick(route) },
                        modifier = Modifier.size(width = 240.dp, height = 100.dp)
                    ) {
                        Text(
                            text = route.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.Center),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}
