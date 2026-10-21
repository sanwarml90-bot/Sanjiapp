package com.sanji.bomberpro.navigation

import androidx.compose.foundation.background
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
import com.sanji.bomberpro.data.UserProfile
import com.sanji.bomberpro.services.ForceJoinService
import com.sanji.bomberpro.services.TermsService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.auth.ForceJoinScreen
import com.sanji.bomberpro.ui.auth.LoginScreen
import com.sanji.bomberpro.ui.auth.TermsScreen
import com.sanji.bomberpro.ui.blast.SmsBlastScreen
import com.sanji.bomberpro.ui.home.HomeScreen
import com.sanji.bomberpro.ui.notifications.NotificationsScreen
import com.sanji.bomberpro.ui.refer.ReferScreen
import com.sanji.bomberpro.ui.splash.SplashScreen
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SMS_BLAST = "sms_blast"
    const val BULK_BLAST = "bulk_blast"
    const val REFER = "refer"
    const val NOTIFICATIONS = "notifications"
    const val TRANSFER = "transfer"
    const val GIFT = "gift"
    const val TEMPLATES = "templates"
    const val GIVEAWAYS = "giveaways"
    const val ANNOUNCEMENTS = "announcements"
    const val USER_SETTINGS = "user_settings"
}

@Composable
fun SanjiNavGraph(
    navController: NavHostController,
    authState: AuthState,
    authManager: AuthManager,
) {
    val colors = LocalSanjiColors.current
    val userData = remember { UserDataService() }
    val termsService = remember { TermsService() }
    val forceJoinService = remember { ForceJoinService() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // term + force-join gate state
    var termsVersion by remember { mutableStateOf(1) }
    var termsRequired by remember { mutableStateOf(true) }
    var forceJoinEnabled by remember { mutableStateOf(false) }
    var gateChecked by remember { mutableStateOf(false) }
    var forceJoinVerified by remember { mutableStateOf(true) }

    // Load settings when user logs in
    LaunchedEffect(authState) {
        val logged = authState as? AuthState.LoggedIn ?: return@LaunchedEffect
        gateChecked = false
        try {
            val settings = userData.getGlobalSettings()
            termsVersion = settings.termsVersion
            termsRequired = settings.termsRequired
            forceJoinEnabled = settings.forceJoinEnabled

            if (forceJoinEnabled) {
                val bypassed = forceJoinService.isUserBypassed(logged.user.uid)
                val verified = forceJoinService.isUserVerified(logged.user.uid)
                forceJoinVerified = bypassed || verified
            } else {
                forceJoinVerified = true
            }
        } catch (_: Exception) {
            termsRequired = false
            forceJoinEnabled = false
            forceJoinVerified = true
        }
        gateChecked = true
    }

    // determine current gate
    val loggedIn = authState as? AuthState.LoggedIn
    val profile: UserProfile? = loggedIn?.profile
    val needsTerms = gateChecked && loggedIn != null &&
            (profile?.needsTermsAcceptance(termsVersion, termsRequired) ?: false)
    val needsForceJoin = gateChecked && loggedIn != null && !needsTerms &&
            forceJoinEnabled && !forceJoinVerified

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // Only show NavHost when user is not behind a gate
        if (!needsTerms && !needsForceJoin) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
            ) {
                composable(Routes.SPLASH) {
                    SplashScreen(
                        onFinished = {
                            val target = when (authState) {
                                is AuthState.LoggedIn -> Routes.HOME
                                else -> Routes.LOGIN
                            }
                            navController.navigate(target) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.LOGIN) {
                    LoginScreen(
                        authManager = authManager,
                        onLoginSuccess = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.HOME) {
                    LaunchedEffect(authState) {
                        if (authState is AuthState.LoggedOut) {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.HOME) { inclusive = true }
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
                                } catch (_: Exception) {
                                    // ignore nav error if route not defined
                                }
                            },
                        )
                    }
                }

                composable(Routes.SMS_BLAST) {
                    SmsBlastScreen(
                        authManager = authManager,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.BULK_BLAST) {
                    ComingSoonScreen(
                        title = "Bulk Blast",
                        message = "Bulk blast mode is coming soon.",
                        onBack = { navController.popBackStack() },
                    )
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

                // ---- placeholders for Session 6-8 ----
                composable(Routes.TRANSFER) {
                    ComingSoonScreen(
                        title = "Transfer",
                        message = "Transfer credits / coins is coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.GIFT) {
                    ComingSoonScreen(
                        title = "Gift Coins",
                        message = "Gift coins to friends is coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.TEMPLATES) {
                    ComingSoonScreen(
                        title = "Templates",
                        message = "Message templates are coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.GIVEAWAYS) {
                    ComingSoonScreen(
                        title = "Giveaways",
                        message = "Giveaways are coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.ANNOUNCEMENTS) {
                    ComingSoonScreen(
                        title = "Announcements",
                        message = "Announcements are coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.USER_SETTINGS) {
                    ComingSoonScreen(
                        title = "Settings",
                        message = "User settings are coming soon.",
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // ---- gate overlays ----
        if (needsTerms) {
            TermsScreen(
                authManager = authManager,
                onAccepted = {
                    scope.launch {
                        // re-check after acceptance
                        val settings = userData.getGlobalSettings()
                        termsVersion = settings.termsVersion
                        termsRequired = settings.termsRequired
                        gateChecked = false
                        kotlinx.coroutines.delay(50)
                        gateChecked = true
                    }
                },
                onDeclined = {
                    // user signed out from TermsScreen
                },
            )
        } else if (needsForceJoin) {
            val logged = authState as? AuthState.LoggedIn
            if (logged != null) {
                ForceJoinScreen(
                    authManager = authManager,
                    uid = logged.user.uid,
                    userName = logged.profile?.displayName ?: (logged.user.displayName ?: "User"),
                    onVerified = {
                        forceJoinVerified = true
                    },
                    onSkip = {
                        // signed out from ForceJoin
                    },
                )
            }
        }
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
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
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