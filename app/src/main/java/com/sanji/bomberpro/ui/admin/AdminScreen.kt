package com.sanji.bomberpro.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.FirebaseNode
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.AdminUser
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminView { HOME, FIREBASE, USERS, USER_DETAIL }

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun AdminScreen(
    adminUid: String,
    isOwner: Boolean,
    onExit: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AdminService() }

    var view by remember { mutableStateOf(AdminView.HOME) }

    var firebases by remember { mutableStateOf<List<FirebaseNode>>(emptyList()) }
    var loadingFb by remember { mutableStateOf(false) }
    var showAddFb by remember { mutableStateOf(false) }

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var userQuery by remember { mutableStateOf("") }
    var loadingUsers by remember { mutableStateOf(false) }

    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    fun loadFirebases() {
        scope.launch {
            loadingFb = true
            firebases = service.listFirebases()
            loadingFb = false
        }
    }

    fun loadUsers() {
        scope.la