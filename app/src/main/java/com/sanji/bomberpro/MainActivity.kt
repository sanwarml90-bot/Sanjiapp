package com.sanji.bomberpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.AuthState
import com.sanji.bomberpro.navigation.SanjiNavGraph
import com.sanji.bomberpro.services.GiveawayTicker
import com.sanji.bomberpro.services.SchedulerTicker
import com.sanji.bomberpro.theme.SanjiTheme
import com.sanji.bomberpro.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private var schedulerTicker: SchedulerTicker? = null
    private var giveawayTicker: GiveawayTicker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themeManager = ThemeManager(this)
        val authManager = AuthManager(this)

        setContent {
            val currentTheme by themeManager.currentTheme
                .collectAsState(initial = themeManager.defaultTheme)
            val authState by authManager.authState
                .collectAsState(initial = AuthState.Initializing)

            SanjiTheme(theme = currentTheme) {
                val navController = rememberNavController()
                val tickerScope = rememberCoroutineScope()

                val schedTicker = remember { SchedulerTicker(tickerScope) }
                val gwTicker = remember { GiveawayTicker(tickerScope) }

                LaunchedEffect(Unit) {
                    schedulerTicker = schedTicker
                    giveawayTicker = gwTicker
                    schedTicker.start()
                    gwTicker.start()
                }

                SanjiNavGraph(
                    navController = navController,
                    authState = authState,
                    authManager = authManager,
                    themeManager = themeManager,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        schedulerTicker?.stop()
        schedulerTicker = null
        giveawayTicker?.stop()
        giveawayTicker = null
    }
}