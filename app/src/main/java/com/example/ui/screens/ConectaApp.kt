package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.GeminiClient
import com.example.data.DiaryEntry
import com.example.data.ExerciseOption
import com.example.ui.viewmodel.ConectaViewModel
import com.example.util.AudioRecorderHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConectaApp(
    viewModel: ConectaViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()

    val recorderHelper = remember { AudioRecorderHelper(context) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Hoy, 1 = Mi Diario

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording(recorderHelper)
        }
    }

    // Clean up playback and recording on dispose
    DisposableEffect(Unit) {
        onDispose {
            recorderHelper.stopRecording()
            recorderHelper.stopPlayback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Hoy") },
                    label = { Text("Hoy") },
                    modifier = Modifier.testTag("nav_tab_hoy")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Mi Diario") },
                    label = { Text("Mi Diario") },
                    modifier = Modifier.testTag("nav_tab_diario")
                )
            }
        }
    ) { innerPadding ->
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "TabTransition") { tab ->
                when (tab) {
                    0 -> WorkoutHub(
                        uiState = uiState,
                        viewModel = viewModel,
                        recorderHelper = recorderHelper,
                        onRecordPermissionRequest = {
                            val permission = Manifest.permission.RECORD_AUDIO
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.startRecording(recorderHelper)
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        }
                    )
                    1 -> DiaryArchiveSet(
                        entries = diaryEntries,
                        uiState = uiState,
                        viewModel = viewModel,
                        recorderHelper = recorderHelper
                    )
                }
            }

            // Error Message Snackbar
            uiState.errorMessage?.let { errorMsg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("OK", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(text = errorMsg)
                }
            }
        }
    }
}

@Composable
fun WorkoutHub(
    uiState: com.example.ui.viewmodel.ConectaUiState,
    viewModel: ConectaViewModel,
    recorderHelper: AudioRecorderHelper,
    onRecordPermissionRequest: () -> Unit
) {
    if (uiState.selectedExercise == null) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AppHeader()
            }

            item {
                HeroGreeting()
            }

            item {
                AIStatusPill()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Misiones del Día",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    IconButton(
                        onClick = { viewModel.loadDailyWorkouts() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refrescar desafíos",
                            tint = Color(0xFF6750A4)
                        )
                    }
                }
            }

            if (uiState.isLoadingWorkouts) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF6750A4))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Invocando al Oráculo del Carisma...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6750A4)
                            )
                        }
                    }
                }
            } else {
                items(uiState.dailyExercises) { exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onStart = { viewModel.selectExercise(exercise) }
                    )
                }
            }

            item {
                PsychologyTipCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else {
        // Exercise view screen
        ExerciseWorkstation(
            exercise = uiState.selectedExercise,
            uiState = uiState,
            viewModel = viewModel,
            recorderHelper = recorderHelper,
            onRecordPermissionRequest = onRecordPermissionRequest
        )
    }
}

@Composable
fun HeroGreeting() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "¡Hola, Alex!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Color(0xFF21005D),
            lineHeight = 36.sp
        )
        Text(
            text = "¿Qué chispa encendemos hoy?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tu misión de 5 minutos te espera. Elige una categoría:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF49454F)
        )
    }
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Conecta5",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF6750A4),
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "MOTOR DE IA SOCIAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7D5260),
                letterSpacing = 1.2.sp
            )
        }
        
        // Hot Streak Badge
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = Color(0xFFFFD8E4),
            modifier = Modifier.height(38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "🔥", fontSize = 16.sp)
                Text(
                    text = "12",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF31111D)
                )
            }
        }
    }
}

@Composable
fun AIStatusPill() {
    val isOnline = GeminiClient.isApiKeyAvailable()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isOnline) Color(0xFFEADDFF).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isOnline) Color(0xFF6750A4).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isOnline) "Motor Inteligente Activo" else "Modo de Elocuencia Local",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isOnline) "Generando consejos adaptativos exclusivos." 
                           else "Para IA en la nube, configura tu GEMINI_API_KEY en panel Secrets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ExerciseListItem(
    exercise: ExerciseOption,
    onStart: () -> Unit
) {
    val config = when {
        exercise.category.contains("Humor", ignoreCase = true) -> Triple(
            Color(0xFFEADDFF), // Card Bg
            Color(0xFF6750A4), // Circle Bg
            Color(0xFF21005D)  // Text Color
        )
        exercise.category.contains("Social", ignoreCase = true) || exercise.category.contains("Conexión", ignoreCase = true) -> Triple(
            Color(0xFFDCE2F9),
            Color(0xFF3F51B5),
            Color(0xFF151B2C)
        )
        exercise.category.contains("Chisme", ignoreCase = true) || exercise.category.contains("Storytelling", ignoreCase = true) -> Triple(
            Color(0xFFFFD8E4),
            Color(0xFF7D5260),
            Color(0xFF31111D)
        )
        // Confianza
        exercise.category.contains("Confianza", ignoreCase = true) -> Triple(
            Color(0xFFE5D5FC),
            Color(0xFF8B5CF6),
            Color(0xFF2C155D)
        )
        // Default
        else -> Triple(
            Color(0xFFFFF3C4),
            Color(0xFFF59E0B),
            Color(0xFF452B00)
        )
    }

    val cardBg = config.first
    val circleBg = config.second
    val textColor = config.third
    val xpText = when {
        exercise.category.contains("Humor", ignoreCase = true) -> "+15 XP"
        exercise.category.contains("Social", ignoreCase = true) -> "+20 XP"
        exercise.category.contains("Chisme", ignoreCase = true) -> "+25 XP"
        else -> "+20 XP"
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise_card_${exercise.id}")
            .clickable { onStart() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle block holding the beautiful emoji
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = exercise.emoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color.White.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = xpText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = exercise.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = exercise.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PsychologyTipCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(text = "💡", fontSize = 20.sp)
            Column {
                Text(
                    text = "Tip Psicológico",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Si te da nervios, baja los hilos de tensión: relaja los hombros y sonríe ligeramente antes de empezar; tu cerebro creerá que estás a salvo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ExerciseWorkstation(
    exercise: ExerciseOption,
    uiState: com.example.ui.viewmodel.ConectaUiState,
    viewModel: ConectaViewModel,
    recorderHelper: AudioRecorderHelper,
    onRecordPermissionRequest: () -> Unit
) {
    val accentColor = when (exercise.category) {
        "Humor y Chispa" -> MaterialTheme.colorScheme.tertiary
        "Carisma y Conexión Social" -> MaterialTheme.colorScheme.primary
        "El Arte del \"Chisme\" y Storytelling", "El Arte del Chisme y Storytelling" -> MaterialTheme.colorScheme.secondary
        "Confianza y Desbloqueo Psicológico" -> Color(0xFF8B5CF6)
        else -> Color(0xFF14B8A6)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header with Back Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { viewModel.deselectExercise() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Misión del Día",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            // Exercise Overview Card
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = exercise.emoji, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = exercise.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accentColor
                            )
                            Text(
                                text = exercise.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "INSTRUCCIONES DE EJERCICIO:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = exercise.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tip box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Text(text = "💡", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SABIDURÍA SOCIAL (TIP)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = exercise.psychologicalTip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.feedbackResponse == null) {
            item {
                // Action Arena
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ESTUDIO DE PRÁCTICA",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Text(
                            text = "Graba tu voz respondiendo a la misión o describe en texto tu práctica para tu diario.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Visual recorder widget
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isRecording) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "🎙️ GRABANDO AUDIO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val minutes = uiState.recordingDuration / 60
                                        val seconds = uiState.recordingDuration % 60
                                        Text(
                                            text = String.format("%02d:%02d", minutes, seconds),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                } else if (uiState.recordedAudioPath != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text("🎙️ Audio de voz listo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                if (uiState.isPlayingAudio) {
                                                    viewModel.stopAudioPlayback(recorderHelper)
                                                } else {
                                                    viewModel.startAudioPlayback(uiState.recordedAudioPath, recorderHelper)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(if (uiState.isPlayingAudio) "⏸️ PAUSAR" else "▶️ REPRODUCIR")
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Micrófono listo para recibir tu talento",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Trigger buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.isRecording) {
                                        viewModel.stopRecording(recorderHelper)
                                    } else {
                                        onRecordPermissionRequest()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else accentColor
                                ),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(52.dp)
                                    .testTag("record_audio_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (uiState.isRecording) "🛑 detener" else "🎙️ GRABAR VOZ")
                                }
                            }

                            if (uiState.recordedAudioPath != null) {
                                OutlinedButton(
                                    onClick = { 
                                        viewModel.selectExercise(exercise) // resets audio
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                ) {
                                    Text("ELIMINAR")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        Spacer(modifier = Modifier.height(10.dp))

                        // Text Note input (Secondary/Simultaneous)
                        Text(
                            text = "Añade notas en texto / Diario escrito (opcional):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        TextField(
                            value = uiState.typedNotes,
                            onValueChange = { viewModel.onNotesChanged(it) },
                            placeholder = { Text("Escribe tus ideas clave, guion u observaciones aquí...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .testTag("notes_text_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Submit action
                        Button(
                            onClick = { viewModel.submitMission() },
                            shape = RoundedCornerShape(14.dp),
                            enabled = !uiState.isSubmitting && (uiState.recordedAudioPath != null || uiState.typedNotes.isNotBlank()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("complete_mission_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = "🚀 COMPLETAR RETO",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Display dynamic Coach Coach Feedback and structural Ficha
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "👑", fontSize = 32.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "RETROALIMENTACIÓN DE TU ENTRENADOR IA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "¡Misión Cumplida!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Feedback message
                        Text(
                            text = uiState.feedbackResponse ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Saved label
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📁", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "ARCHIVADO AUTOMÁTICAMENTE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Guardado con éxito en tu Diario Personal.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { viewModel.deselectExercise() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Hacer otro desafío diario ⚡")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryArchiveSet(
    entries: List<DiaryEntry>,
    uiState: com.example.ui.viewmodel.ConectaUiState,
    viewModel: ConectaViewModel,
    recorderHelper: AudioRecorderHelper
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AppHeader()

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Mi Diario Personal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Aquí habitan todos tus adiestramientos, notas y audios guardados cronológicamente sin juicios ni evaluaciones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(text = "✍️", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tu diario está sin registrar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Completa tu primer reto del día de 5 minutos en el panel 'Hoy' para archivar tu primer ejercicio de carisma.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DiaryEntryItem(
                        entry = entry,
                        uiState = uiState,
                        onDelete = { viewModel.deleteDiaryEntry(entry) },
                        onPlayAudio = {
                            if (uiState.isPlayingAudio && uiState.playingAudioPath == entry.audioPath) {
                                viewModel.stopAudioPlayback(recorderHelper)
                            } else {
                                entry.audioPath?.let { viewModel.startAudioPlayback(it, recorderHelper) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryEntryItem(
    entry: DiaryEntry,
    uiState: com.example.ui.viewmodel.ConectaUiState,
    onDelete: () -> Unit,
    onPlayAudio: () -> Unit
) {
    val dateStr = remember(entry.timestamp) {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", java.util.Locale.getDefault())
        sdf.format(java.util.Date(entry.timestamp))
    }

    val badgeColor = when (entry.enfoque) {
        "Humor y Chispa", "Humor" -> MaterialTheme.colorScheme.tertiary
        "Carisma y Conexión Social", "Social" -> MaterialTheme.colorScheme.primary
        "El Arte del \"Chisme\" y Storytelling", "El Arte del Chisme y Storytelling", "Storytelling" -> MaterialTheme.colorScheme.secondary
        "Confianza y Desbloqueo Psicológico", "Confianza" -> Color(0xFF8B5CF6)
        else -> Color(0xFF14B8A6)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diary_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = entry.enfoque,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("delete_diary_entry_${entry.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Borrar nota",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = entry.mision,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (entry.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Playback if recorded audio exists
            if (entry.audioPath != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isCurrentPlaying = uiState.isPlayingAudio && uiState.playingAudioPath == entry.audioPath
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAudio() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isCurrentPlaying) "🔊" else "🔈", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isCurrentPlaying) "Reproduciendo audio..." else "Escuchar grabación de oratoria",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isCurrentPlaying) "⏸️ DETENER" else "▶️ OÍR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Tip box
            if (entry.tip.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Tip archivado: ${entry.tip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
