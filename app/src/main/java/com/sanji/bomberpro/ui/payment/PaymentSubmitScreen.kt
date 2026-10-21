package com.sanji.bomberpro.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanji.bomberpro.data.PaymentRequest
import com.sanji.bomberpro.services.PaymentService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun PaymentSubmitScreen(
    uid: String,
    userName: String,
    userEmail: String,
    type: String,
    packId: String,
    packName: String,
    creditsAmount: Int,
    plan: String,
    amountInr: Int,
    upiId: String,
    upiName: String,
    onClose: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { PaymentService(context) }

    var utr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submittedRequest by remember { mutableStateOf<PaymentRequest?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) screenshotUri = uri }

    val upiUri = "upi://pay?pa=${Uri.encode(upiId)}&pn=${Uri.encode(upiName)}&am=$amountInr&cu=INR"
    val qrUrl = if (upiId.isNotBlank())
        "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${Uri.encode(upiUri)}"
    else ""

    val activeRequest = submittedRequest
    if (activeRequest != null) {
        var liveStatus by remember(activeRequest.id) { mutableStateOf(activeRequest.status) }
        LaunchedEffect(activeRequest.id) {
            service.observeRequest(activeRequest.id).collect { r ->
                if (r != null) liveStatus = r.status
            }
        }
        StatusScreen(
            status = liveStatus,
            itemName = if (type == "subscription") plan else packName,
            amountInr = amountInr,
            onClose = onClose,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = colors.onBackground)
            }
            Text(
                "Payment",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(colors = listOf(colors.primary, colors.secondary)))
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        if (type == "subscription") "SUBSCRIPTION" else "CREDITS PACK",
                        fontSize = 10.sp, color = Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (type == "subscription") plan.uppercase() else packName,
                        fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("₹$amountInr", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("1. Pay via UPI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(8.dp))

        if (upiId.isBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("UPI not configured yet.", fontSize = 12.sp, color = colors.error)
                    Text("Contact admin to make payment.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("UPI ID", fontSize = 10.sp, color = colors.onSurfaceVariant)
                        Text(
                            upiId, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = colors.primary, fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Name: ${upiName.ifBlank { "Admin" }}", fontSize = 11.sp, color = colors.onBackground)
                    }
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("UPI", upiId))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = colors.primary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        error = "No UPI app installed"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Payment, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pay ₹$amountInr via UPI App", fontWeight = FontWeight.Bold)
            }

            if (qrUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                        AsyncImage(
                            model = qrUrl,
                            contentDescription = "QR",
                            modifier = Modifier.padding(10.dp).size(200.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("2. Submit confirmation", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = utr,
            onValueChange = { utr = it.take(40) },
            label = { Text("UPI Transaction ID / UTR") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(200) },
            label = { Text("Note (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            onClick = { imagePicker.launch("image/*") },
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (screenshotUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    AsyncImage(
                        model = screenshotUri,
                        contentDescription = "Screenshot preview",
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Text(
                            "Change",
                            fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Image, null, tint = colors.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to upload payment screenshot", fontSize = 12.sp, color = colors.onBackground, fontWeight = FontWeight.SemiBold)
                    Text("Required · PNG or JPG", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (utr.isBlank()) { error = "UTR required"; return@Button }
                if (screenshotUri == null) { error = "Screenshot required"; return@Button }
                submitting = true
                error = null
                scope.launch {
                    val result = service.submitPayment(
                        uid = uid, userName = userName, userEmail = userEmail,
                        type = type, packId = packId, packName = packName,
                        creditsAmount = creditsAmount, plan = plan, amountInr = amountInr,
                        utr = utr.trim(), description = description.trim(),
                        screenshotUri = screenshotUri!!,
                    )
                    submitting = false
                    if (result.isSuccess) {
                        submittedRequest = result.getOrNull()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Submit failed"
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
        ) {
            if (submitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Submitting…", fontWeight = FontWeight.Bold)
            } else {
                Text("Submit Payment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusScreen(
    status: String,
    itemName: String,
    amountInr: Int,
    onClose: () -> Unit,
) {
    val colors = LocalSanjiColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (status) {
            "approved" -> {
                Icon(Icons.Default.CheckCircle, null, tint = colors.success, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(20.dp))
                Text("Payment Approved", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$itemName activated\n₹$amountInr confirmed",
                    fontSize = 13.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                ) { Text("Done", fontWeight = FontWeight.Bold) }
            }
            "rejected" -> {
                Text("❌", fontSize = 60.sp)
                Spacer(Modifier.height(16.dp))
                Text("Payment Rejected", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.error)
                Spacer(Modifier.height(8.dp))
                Text("Contact support if this seems wrong", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                ) { Text("Close", fontWeight = FontWeight.Bold) }
            }
            else -> {
                CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(24.dp))
                Text("Awaiting Approval", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Admin will review and approve shortly.\nThis screen will update automatically.",
                    fontSize = 13.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.onBackground),
                ) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}