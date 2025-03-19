package com.ownid.demo.gigya

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ownid.demo.AppActivityContent
import com.ownid.demo.gigya.screen.auth.AuthViewModel
import com.ownid.demo.gigya.screen.auth.ConflictingAccountScreen
import com.ownid.demo.gigya.screen.auth.LoginScreen
import com.ownid.demo.gigya.screen.auth.ProfileCollectionScreen
import com.ownid.demo.gigya.screen.auth.RegistrationScreen
import com.ownid.demo.gigya.screen.auth.WelcomeScreen
import com.ownid.demo.gigya.screen.home.HomeViewModel
import com.ownid.demo.gigya.screen.home.ProfileScreen
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable

class AppActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppActivityContent {
                AppContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    onError = ::showError
                )
            }
        }
    }
}

sealed class AppScreen {
    @Serializable
    open class Auth : AppScreen() {
        @Serializable
        data object Welcome : Auth()

        @Serializable
        data object Login : Auth()

        @Serializable
        data object Registration : Auth()

        @Serializable
        data class ConflictingAccount(val loginId: String, val message: String) : Auth()

        @Serializable
        data class ProfileCollection(val loginId: String) : Auth()
    }

    @Serializable
    open class Home : AppScreen() {
        @Serializable
        data class Profile(val name: String, val email: String) : AppScreen()
    }
}

@Composable
private fun AppContent(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onError: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.Auth::class,
        modifier = modifier
    ) {
        navigation<AppScreen.Auth>(startDestination = AppScreen.Auth.Welcome::class) {
            composable<AppScreen.Auth.Welcome> { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Auth>() }
                val authViewModel = viewModel<AuthViewModel>(parentEntry)
                WelcomeScreen(
                    onNavigateToRegistration = { navController.navigate(AppScreen.Auth.Registration) },
                    onNavigateToLogin = { navController.navigate(AppScreen.Auth.Login) },
                    runOwnIdFlow = authViewModel::runOwnIdFlow
                )
                NavigationEffect(navController, authViewModel, onError)
            }
            composable<AppScreen.Auth.Registration> { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Auth>() }
                val authViewModel = viewModel<AuthViewModel>(parentEntry)
                RegistrationScreen(
                    doRegisterWithPassword = authViewModel::doRegisterWithPassword,
                    onOwnIdLogin = authViewModel::onOwnIdLogin,
                    onOwnIdError = authViewModel::onOwnIdError,
                    onNavigateBack = navController::navigateUp,
                )
                NavigationEffect(navController, authViewModel, onError)
            }
            composable<AppScreen.Auth.Login> { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Auth>() }
                val authViewModel = viewModel<AuthViewModel>(parentEntry)
                LoginScreen(
                    doLoginWithPassword = authViewModel::doLoginWithPassword,
                    onSignInWithGoogle = authViewModel::onSignInWithGoogle,
                    onOwnIdLogin = authViewModel::onOwnIdLogin,
                    onOwnIdError = authViewModel::onOwnIdError,
                    onNavigateBack = navController::navigateUp,
                )
                NavigationEffect(navController, authViewModel, onError)
            }
            dialog<AppScreen.Auth.ConflictingAccount> { backStackEntry ->
                val (conflictLoginId, conflictMessage) = backStackEntry.toRoute<AppScreen.Auth.ConflictingAccount>()
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Auth>() }
                val authViewModel = viewModel<AuthViewModel>(parentEntry)
                ConflictingAccountScreen(
                    conflictMessage = conflictMessage,
                    conflictLoginId = conflictLoginId,
                    onOwnIdLogin = authViewModel::onOwnIdLogin,
                    onOwnIdError = authViewModel::onOwnIdError
                )
                DisposableEffect(authViewModel) {
                    onDispose { authViewModel.clearState() }
                }
            }
            dialog<AppScreen.Auth.ProfileCollection> { backStackEntry ->
                val (loginId) = backStackEntry.toRoute<AppScreen.Auth.ProfileCollection>()
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Auth>() }
                val authViewModel = viewModel<AuthViewModel>(parentEntry)
                ProfileCollectionScreen(
                    email = loginId,
                    finishRegisterWithOwnId = authViewModel::finishRegisterWithOwnId
                )
                DisposableEffect(authViewModel) {
                    onDispose { authViewModel.clearState() }
                }
            }
        }

        navigation<AppScreen.Home>(startDestination = AppScreen.Home.Profile::class) {
            composable<AppScreen.Home.Profile> { backStackEntry ->
                val (name, email) = backStackEntry.toRoute<AppScreen.Home.Profile>()
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<AppScreen.Home>() }
                val homeViewModel = viewModel<HomeViewModel>(parentEntry)
                ProfileScreen(
                    name = name,
                    email = email,
                    doLogout = {
                        homeViewModel.doLogout()
                        navController.navigate(AppScreen.Auth.Welcome) {
                            popUpTo<AppScreen.Home> { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavigationEffect(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    showError: (String) -> Unit
) {
    LaunchedEffect(navController, authViewModel) {
        authViewModel.uiStateFlow.filterNotNull().onEach { uiState ->
            Log.e("AppContent", "LaunchedEffect.Login.onEach: $uiState")

            if (uiState is AuthViewModel.UiState.LoggedIn) {
                navController.navigate(AppScreen.Home.Profile(uiState.name, uiState.email)) {
                    popUpTo<AppScreen.Auth> { inclusive = true }
                }
            }
            if (uiState is AuthViewModel.UiState.Error) {
                showError(uiState.error)
                authViewModel.clearState()
            }
            if (uiState is AuthViewModel.UiState.ConflictingAccount) {
                navController.navigate(AppScreen.Auth.ConflictingAccount(uiState.loginId, uiState.message))
            }
            if (uiState is AuthViewModel.UiState.OnAccountNotFound) {
                navController.navigate(AppScreen.Auth.ProfileCollection(uiState.loginId))
            }
        }
            .launchIn(this)
    }
}