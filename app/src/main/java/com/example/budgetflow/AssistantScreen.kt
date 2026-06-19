package com.example.budgetflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val uiState   by viewModel.uiState.collectAsState()
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText   by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // ── SpeechRecognizer setup ────────────────────────────────────────────────
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context)
        else null
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                inputText = text
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                inputText = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer?.destroy() }
    }

    // Mic permission
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speechRecognizer?.startListening(recognizerIntent)
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("BUDGETFLOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 3.sp)
            Spacer(Modifier.height(2.dp))
            Text("Assistant", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }

        // Config error banner
        if (uiState.configError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFBBF24).copy(alpha = 0.15f)
            ) {
                Text(
                    uiState.configError!!,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = Color(0xFFFBBF24)
                )
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
        }

        // Input bar
        InputBar(
            text          = inputText,
            onTextChange  = { inputText = it },
            isLoading     = uiState.isLoading,
            isListening   = isListening,
            onSend        = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            onMicToggle   = {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                } else {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) speechRecognizer?.startListening(recognizerIntent)
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
    }
}

// ─── Chat bubble ─────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Assistant avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.2f))
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 14.sp, color = Accent)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd   = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) Accent else BgCard
            ) {
                if (msg.isLoading) {
                    ThinkingIndicator()
                } else {
                    Text(
                        text     = msg.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        color    = if (isUser) Color.White else TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }

            // Action chips — show what was created
            if (msg.actions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                msg.actions.forEach { action ->
                    ActionChip(action)
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.15f))
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ActionChip(action: AgentActionPayload) {
    val (emoji, label) = when (action.type) {
        "ADD_TRANSACTION"  -> "✅" to "Transaction added: ${action.description} ₹${action.amount?.toInt()}"
        "ADD_BUDGET_ENTRY" -> "✅" to "Budget source added: ${action.name} ₹${action.amount?.toInt()}"
        "QUERY_STATS"      -> return  // answer shown in the main bubble
        "ERROR"            -> "⚠️" to (action.message ?: "Error")
        else               -> return
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (action.type == "ERROR") AccentRed.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.12f)
    ) {
        Text(
            "$emoji $label",
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize  = 11.sp,
            color     = if (action.type == "ERROR") AccentRed else AccentGreen,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(400, delayMillis = i * 120),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(TextMuted)
            )
        }
    }
}

// ─── Input bar ────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    onSend: () -> Unit,
    onMicToggle: () -> Unit
) {
    Surface(
        color = BgCard,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Mic button
            val micScale by animateFloatAsState(
                targetValue = if (isListening) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "mic_scale"
            )
            IconButton(
                onClick  = onMicToggle,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            if (isListening) AccentRed.copy(alpha = 0.2f)
                            else BgCardAlt
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (isListening) painterResource(R.drawable.outline_mic_off_24) else painterResource(R.drawable.outline_mic_24),
                        contentDescription = if (isListening) "Stop listening" else "Voice input",
                        tint = if (isListening) AccentRed else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Text field
            OutlinedTextField(
                value         = text,
                onValueChange = onTextChange,
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    Text(
                        if (isListening) "Listening…" else "Ask me anything…",
                        color = TextMuted, fontSize = 14.sp
                    )
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor    = TextPrimary,
                    unfocusedTextColor  = TextPrimary,
                    focusedBorderColor  = Accent,
                    unfocusedBorderColor = Divider,
                    cursorColor         = Accent
                ),
                shape         = RoundedCornerShape(24.dp),
                maxLines      = 4,
                enabled       = !isLoading
            )

            Spacer(Modifier.width(6.dp))

            // Send button
            val canSend = text.isNotBlank() && !isLoading
            IconButton(
                onClick  = onSend,
                enabled  = canSend,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Accent else BgCardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint               = if (canSend) Color.White else TextMuted,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}