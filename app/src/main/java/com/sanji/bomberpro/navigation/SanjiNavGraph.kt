package com.sanji.bomberpro.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.AuthState
import com.sanji.bomberpro.services.ForceJoinService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.theme.ThemeManager
import com.sanji.bomberpro.ui.announcements.AnnouncementsScreen
import com.sanji.bomberpro.ui.appeal.AppealScreen
import com.sanji.bomberpro.ui.auth.ForceJoinScreen
import com.sanji.bomberpro.ui.auth.LoginScreen
import com.sanji.bomberpro.ui.auth.TermsScreen
import com.sanji.bomberpro.ui.blast.BulkBlastScreen
import com.sanji.bomberpro.ui.blast.ScheduleBlastScreen
import com.sanji.bomberpro.ui.blast.SmsBlastScreen
import com.sanji.bomberpro.ui.home.HomeScreen
import com.sanji.bomberpro.ui.notifications.NotificationsScreen
import com.sanji.bomberpro.ui.refer.ReferScreen
import com.sanji.bomberpro.ui.settings.UserSettingsScreen
import com.sanji.bomberpro.ui.splash.SplashScreen
import com.sanji.bomberpro.ui.templates.TemplatesScreen
import com.sanji.bomberpro.ui.wallet.GiftScreen
import com.sanji.bomberpro.ui.wallet.TransferScreen
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val TERMS = "terms"
    const val FORCE_JOIN = "force_join"
    const val HOME = "home"
    const val SMS_BLAST = "sms_blast"
    const val BULK_BLAST = "bulk_blast"
    const val SCHEDULE_BLAST = "schedule_blast"
    const val REFER = "refer"
    const val NOTIFICATIONS = "notifications"
    const val TRANSFER = "transfer"
    const val GIFT = "gift"
    const val TEMPLATES = "templates"
    const val GIVEAWAYS = "giveaways"
    const val ANNOUNCEMENTS = "announcements"
    const val USER_SETTINGS = "user_settings"
    const val APPEAL = "appeal"
}

@Composable
fun SanjiNavGraph(
    navController: NavHostController,
    authState: AuthState,
    authManager: AuthManager,
    themeManager: ThemeManager,
) {
    val colors = LocalSanjiColors.current
    val userData = remember { UserDataService() }
    val forceJoinService = remember { ForceJoinService() }
    val scope = rememberCoroutineScope()

    var termsVersion by remember { mutableStateOf(1) }
    var termsRequired by remember { mutableStateOf(true) }
    var forceJoinEnabled by remember { mutableStateOf(false) }
    var gateLoaded by remember { mutableStateOf(false) }

    // load gate settings whenever user is logged in
    LaunchedEffect(authState) {
        val logged = authState as? AuthState.LoggedIn ?: return@LaunchedEffect
        gateLoaded = false
        try {
            val s = userData.getGlobalSettings()
            termsVersion = s.termsVersion
            termsRequired = s.termsRequired
            forceJoinEnabled = s.forceJoinEnabled
        } catch (_: Exception) {
            termsRequired = true
            forceJoinEnabled = false
        }
        gateLoaded = true
    }

    val loggedIn = authState as? AuthState.LoggedIn
    // IMPORTANT: if profile is null (race on first signup), treat as needing terms.
    // Bot parity + robustness.
    val needsTerms = gateLoaded && loggedIn != null && termsRequired &&
            (loggedIn.profile?.termsAccepted != true ||
                    (loggedIn.profile?.termsVersion ?: 0) < termsVersion)

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val target = computeInitialRoute(
                        authState = authState,
                        gateLoaded = gateLoaded,
                        needsTerms = needsTerms,
                        forceJoinEnabled = forceJoinEnabled,
                    )
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.TERMS) {
            if (loggedIn == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.TERMS) { inclusive = true }
                    }
                }
            } else {
                TermsScreen(
                    authManager = authManager,
                    onAccepted = {
                        scope.launch {
                            val s = userData.getGlobalSettings()
                            termsVersion = s.termsVersion
                            termsRequired = s.termsRequired
                            forceJoinEnabled = s.forceJoinEnabled

                            val next = if (
                                forceJoinEnabled &&
                                !forceJoinService.isUserBypassed(loggedIn.user.uid) &&
                                !forceJoinService.isUserVerified(loggedIn.user.uid)
                            ) Routes.FORCE_JOIN else Routes.HOME
                            navController.navigate(next) {
                                popUpTo(Routes.TERMS) { inclusive = true }
                            }
                        }
                    },
                    onDeclined = {
                        authManager.signOut()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Routes.FORCE_JOIN) {
            if (loggedIn == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.FORCE_JOIN) { inclusive = true }
                    }
                }
            } else {
                ForceJoinScreen(
                    authManager = authManager,
                    uid = loggedIn.user.uid,
                    userName = loggedIn.profile?.displayName
                        ?: (loggedIn.user.displayName ?: "User"),
                    onVerified = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.FORCE_JOIN) { inclusive = true }
                        }
                    },
                    onSkip = {
                        authManager.signOut()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                authManager = authManager,
                onLoginSuccess = {
                    // after login, decide gate
                    if (needsTerms) {
                        navController.navigate(Routes.TERMS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    } else if (forceJoinEnabled) {
                        navController.navigate(Routes.FORCE_JOIN) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                HomeScreen(
                    user = logged.user,
                    role = logged.role,
                    profile = logged.profile,
                    authManager = authManager,
                    onNavigate = { route ->
                        try {
                            navController.navigate(route)
                        } catch (_: Exception) {}
                    },
                )
            }
        }

        composable(Routes.SMS_BLAST) {
            SmsBlastScreen(
                authManager = authManager,
                onBack = { navController.popBackStack() },
                onOpenBulkMode = {
                    navController.navigate(Routes.BULK_BLAST)
                },
            )
        }

        composable(Routes.BULK_BLAST) {
            BulkBlastScreen(
                authManager = authManager,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SCHEDULE_BLAST) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                ScheduleBlastScreen(
                    uid = logged.user.uid,
                    userName = logged.profile?.displayName
                        ?: (logged.user.displayName ?: "User"),
                    userEmail = logged.profile?.email ?: (logged.user.email ?: ""),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.REFER) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                ReferScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.NOTIFICATIONS) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                NotificationsScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.TRANSFER) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                TransferScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.GIFT) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                GiftScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.TEMPLATES) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                TemplatesScreen(
                    uid = logged.user.uid,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.GIVEAWAYS) {
            ComingSoonScreen(
                title = "Giveaways",
                message = "Giveaways coming in a future update.",
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ANNOUNCEMENTS) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                val isAdmin = logged.role.priority >= com.sanji.bomberpro.data.UserRole.ADMIN.priority
                AnnouncementsScreen(
                    uid = logged.user.uid,
                    isAdmin = isAdmin,
                    adminUid = logged.user.uid,
                    adminName = logged.profile?.displayName
                        ?: (logged.user.displayName ?: "Admin"),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.USER_SETTINGS) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                UserSettingsScreen(
                    authManager = authManager,
                    themeManager = themeManager,
                    profile = logged.profile,
                    onBack = { navController.popBackStack() },
                    onSignedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Routes.APPEAL) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                AppealScreen(
                    uid = logged.user.uid,
                    userName = logged.profile?.displayName
                        ?: (logged.user.displayName ?: "User"),
                    userEmail = logged.profile?.email ?: (logged.user.email ?: ""),
                    banReason = logged.profile?.banReason ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun computeInitialRoute(
    authState: AuthState,
    gateLoaded: Boolean,
    needsTerms: Boolean,
    forceJoinEnabled: Boolean,
): String {
    return when (authState) {
        is AuthState.LoggedIn -> {
            if (!gateLoaded) Routes.HOME
            else if (needsTerms) Routes.TERMS
            else if (forceJoinEnabled) Routes.FORCE_JOIN
            else Routes.HOME
        }
        else -> Routes.LOGIN
    }
}

@Composable
private fun ComingSoonScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBack) {
            Text("← Back", color = colors.primary)
        }
    }
}