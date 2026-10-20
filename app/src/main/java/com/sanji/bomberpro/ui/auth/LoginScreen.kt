package com.sanji.bomberpro.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.AuthState
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authState by authManager.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        when (val s = authState) {
            is AuthState.LoggedIn -> {
                error = null
                info = null
                loading = false
                onLoginSuccess()
            }
            is AuthState.Error -> {
                error = s.message
                loading = false
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.background,
                        colors.primary.copy(alpha = 0.08f),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                text = "SANJI BOMBER",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "PRO",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
                color = colors.primary,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Fast. Private. Unlimited.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // ============================================================
            //  GOOGLE SIGN IN
            // ============================================================
            Button(
                onClick = {
                    loading = true
                    error = null
                    info = null
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)

                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(FirebaseConfig.WEB_CLIENT_ID)
                                .setAutoSelectEnabled(false)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(
                                context = context,
                                request = request,
                            )

                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val googleCred =
                                    GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleCred.idToken

                                val signInResult = authManager.signInWithGoogleIdToken(idToken)
                                if (signInResult.isFailure) {
                                    error = signInResult.exceptionOrNull()?.message
                                        ?: "Google sign-in failed"
                                    loading = false
                                }
                            } else {
                                error = "Unexpected credential type"
                                loading = false
                            }
                        } catch (e: GetCredentialException) {
                            error = "Google sign-in setup incomplete. Email/password use karo."
                            loading = false
                        } catch (e: Exception) {
                            error = e.message ?: "Google sign-in failed"
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                } else {
                    Text(
                        text = "Continue with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ============================================================
            //  DIVIDER
            // ============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Divider(
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant.copy(alpha = 0.3f),
                )
                Text(
                    text = "  OR  ",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
                Divider(
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ============================================================
            //  EMAIL + PASSWORD
            // ============================================================
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    error = null
                    info = null
                    if (email.isBlank() || !email.contains("@")) {
                        error = "Valid email enter kar"
                        return@Button
                    }
                    if (password.length < 6) {
                        error = "Password min 6 characters"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        val result = authManager.signInOrSignUpWithEmail(email, password)
                        if (result.isFailure) {
                            error = result.exceptionOrNull()?.message ?: "Login failed"
                        }
                        loading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                } else {
                    Text(
                        text = "Sign In / Sign Up",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Naya account? Wahi email + password daal — auto register ho jaayega.",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error!!,
                    color = colors.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (info != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = info!!,
                    color = colors.primary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "By continuing, you agree to our Terms & Privacy Policy",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}