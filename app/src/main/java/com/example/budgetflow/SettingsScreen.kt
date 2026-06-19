package com.example.budgetflow

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSettingsSaved: (() -> Unit)? = null
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val secureStorage = remember { SecureStorage.getInstance(context) }

    var geminiApiKey  by remember { mutableStateOf("") }
    var serverUrl     by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var isSaving      by remember { mutableStateOf(false) }

    // Load persisted values once on entry
    LaunchedEffect(Unit) {
        geminiApiKey = secureStorage.getString("geminiApiKey")
        serverUrl    = getServerUrl(context).ifBlank { "http://10.0.2.2:3000" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text("BUDGETFLOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 3.sp)
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)

        // ── AI Assistant card ─────────────────────────────────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("AI Assistant", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                // Security note
                Surface(shape = RoundedCornerShape(10.dp), color = AccentGreen.copy(alpha = 0.08f)) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒", fontSize = 14.sp)
                        Text(
                            "API key is encrypted on-device using AES-256-GCM " +
                                    "backed by the Android Keystore. It never leaves your device unencrypted.",
                            fontSize = 11.sp, color = AccentGreen, lineHeight = 16.sp
                        )
                    }
                }

                // Gemini API Key field
                OutlinedTextField(
                    value         = geminiApiKey,
                    onValueChange = { geminiApiKey = it },
                    label         = { Text("Gemini API Key", color = TextMuted) },
                    placeholder   = { Text("AIza...", color = TextMuted.copy(alpha = 0.5f)) },
                    singleLine    = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon  = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (apiKeyVisible) R.drawable.outline_visibility_off_24
                                    else R.drawable.outline_visibility_24
                                ),
                                contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                tint = TextMuted
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors   = bfTextFieldColors(),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Backend server URL field
                OutlinedTextField(
                    value         = serverUrl,
                    onValueChange = { serverUrl = it },
                    label         = { Text("Backend Server URL", color = TextMuted) },
                    placeholder   = { Text("http://192.168.1.x:3000", color = TextMuted.copy(alpha = 0.5f)) },
                    singleLine    = true,
                    supportingText = {
                        Text(
                            "Emulator → http://10.0.2.2:3000 · Real device → your machine's local IP",
                            fontSize = 11.sp, color = TextMuted.copy(alpha = 0.7f)
                        )
                    },
                    colors   = bfTextFieldColors(),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // API key hint
                Surface(shape = RoundedCornerShape(10.dp), color = Accent.copy(alpha = 0.08f)) {
                    Text(
                        "Get a free Gemini API key at aistudio.google.com",
                        modifier  = Modifier.padding(10.dp),
                        fontSize  = 11.sp,
                        color     = Accent
                    )
                }

                // Save button
                // saveString() is synchronous (commit()), so no coroutine needed for the key.
                // saveServerUrl() is a suspend fun (DataStore), so we still need the scope.
                Button(
                    onClick = {
                        isSaving = true
                        // Save API key synchronously — guaranteed on disk before we proceed
                        secureStorage.saveString("geminiApiKey", geminiApiKey.trim())
                        scope.launch {
                            saveServerUrl(context, serverUrl.trim())
                            isSaving = false
                            onSettingsSaved?.invoke()
                            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled  = !isSaving,
                    colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── About card ────────────────────────────────────────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                SettingsRow("App",        "BudgetFlow")
                SettingsRow("AI Model",   "Gemini 2.5 Flash")
                SettingsRow("AI Backend", "LangChain.js + Express")
                SettingsRow("Storage",    "DataStore + EncryptedSharedPreferences")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextMuted)
        Text(value, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}