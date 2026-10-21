package com.sanji.bomberpro.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.AuthState
import com.sanji.bomberpro.ui.auth.LoginScreen
import com.sanji.bomberpro.ui.blast.SmsBlastScreen
import com.sanji.bomberpro.ui.home.HomeScreen
import com.sanji.bomberpro.ui.refer.ReferScreen
import com.sanji.bomberpro.ui.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SMS_BLAST = "sms_blast"
    const val REFER = "refer"
}

@Composable
fun SanjiNavGraph(
    navController: NavHostController,
    authState: AuthState,
    authManager: AuthManager,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val target = if (authState is AuthState.LoggedIn) Routes.HOME else Routes.LOGIN
                    navController.navigate(target) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                authManager = authManager,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(Routes.HOME) {
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
                }
            }
            val logged = authState
            if (logged is AuthState.LoggedIn) {
                HomeScreen(
                    user = logged.user,
                    role = logged.role,
                    profile = logged.profile,
                    authManager = authManager,
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
        }

        composable(Routes.SMS_BLAST) {
            SmsBlastScreen(
                authManager = authManager,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.REFER) {
            val logged = authState
            if (logged is AuthState.LoggedIn) {
                ReferScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}