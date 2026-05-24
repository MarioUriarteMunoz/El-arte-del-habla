package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.DiaryEntry
import com.example.data.DiaryRepository
import com.example.data.ExerciseOption
import com.example.util.AudioRecorderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConectaUiState(
    val dailyExercises: List<ExerciseOption> = emptyList(),
    val isLoadingWorkouts: Boolean = false,
    val selectedExercise: ExerciseOption? = null,
    val isRecording: Boolean = false,
    val recordingDuration: Int = 0,
    val recordedAudioPath: String? = null,
    val typedNotes: String = "",
    val isSubmitting: Boolean = false,
    val feedbackResponse: String? = null,
    val showFichaSavedNotification: Boolean = false,
    val errorMessage: String? = null,
    // Audio Player states (for practicing or review)
    val playingAudioPath: String? = null,
    val isPlayingAudio: Boolean = false
)

class ConectaViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ConectaViewModel"
    private val repository: DiaryRepository

    private val _uiState = MutableStateFlow(ConectaUiState())
    val uiState: StateFlow<ConectaUiState> = _uiState.asStateFlow()

    // Expose Room database diary entries reactively
    val diaryEntries: StateFlow<List<DiaryEntry>>

    private var recordingJob: Job? = null

    init {
        Log.d(TAG, "Initializing ConectaViewModel")
        val database = AppDatabase.getDatabase(application)
        repository = DiaryRepository(database.diaryDao())

        diaryEntries = repository.allEntries
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        loadDailyWorkouts()
    }

    fun loadDailyWorkouts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkouts = true, errorMessage = null) }
            try {
                val workouts = GeminiClient.generateDailyWorkouts()
                _uiState.update {
                    it.copy(
                        dailyExercises = workouts,
                        isLoadingWorkouts = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading daily workouts", e)
                _uiState.update {
                    it.copy(
                        isLoadingWorkouts = false,
                        errorMessage = "Error de conexión. Mostrando ejercicios locales confiables."
                    )
                }
            }
        }
    }

    fun selectExercise(exercise: ExerciseOption) {
        _uiState.update {
            it.copy(
                selectedExercise = exercise,
                recordedAudioPath = null,
                typedNotes = "",
                feedbackResponse = null,
                showFichaSavedNotification = false,
                isRecording = false,
                recordingDuration = 0
            )
        }
        stopRecordingJob()
    }

    fun deselectExercise() {
        _uiState.update {
            it.copy(
                selectedExercise = null,
                feedbackResponse = null,
                showFichaSavedNotification = false
            )
        }
        stopRecordingJob()
    }

    fun startRecording(recorderHelper: AudioRecorderHelper) {
        viewModelScope.launch {
            val path = recorderHelper.startRecording()
            if (path != null) {
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        recordingDuration = 0,
                        recordedAudioPath = path
                    )
                }
                startRecordingTicker()
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Fracasó el inicio del micrófono. Verifica los permisos.")
                }
            }
        }
    }

    fun stopRecording(recorderHelper: AudioRecorderHelper) {
        viewModelScope.launch {
            recorderHelper.stopRecording()
            stopRecordingJob()
            _uiState.update {
                it.copy(isRecording = false)
            }
        }
    }

    private fun startRecordingTicker() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update {
                    it.copy(recordingDuration = it.recordingDuration + 1)
                }
            }
        }
    }

    private fun stopRecordingJob() {
        recordingJob?.cancel()
        recordingJob = null
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(typedNotes = text) }
    }

    fun submitMission() {
        val currentState = _uiState.value
        val exercise = currentState.selectedExercise ?: return
        
        // Ensure some response was submitted
        if (currentState.recordedAudioPath == null && currentState.typedNotes.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Escribe una nota o graba un audio para guardar en tu diario.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                val inputToCoach = if (currentState.typedNotes.isNotBlank()) {
                    currentState.typedNotes
                } else {
                    "Nota de voz grabada de ${currentState.recordingDuration} segundos."
                }

                val resultFeedback = GeminiClient.generateDynamicFeedback(
                    misionName = exercise.title,
                    enfoque = exercise.category,
                    psychTip = exercise.psychologicalTip,
                    userText = inputToCoach
                )

                // Parse out the fields for Room save
                var finalMision = exercise.title
                var finalEnfoque = exercise.category
                var finalTip = exercise.psychologicalTip

                try {
                    val index = resultFeedback.indexOf("[FICHA DE ARCHIVO]")
                    if (index != -1) {
                        val block = resultFeedback.substring(index)
                        val lines = block.split("\n")
                        for (line in lines) {
                            if (line.contains("- Misión:") || line.contains("- Mision:")) {
                                finalMision = line.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                            } else if (line.contains("- Enfoque:")) {
                                finalEnfoque = line.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                            } else if (line.contains("- Tip del día:") || line.contains("- Tip:")) {
                                finalTip = line.substringAfter(":").trim().removePrefix("[").removeSuffix("]")
                            }
                        }
                    }
                } catch (pe: Exception) {
                    Log.e(TAG, "Parser anomaly on feedback ficha", pe)
                }

                // Insert in Room database
                val entry = DiaryEntry(
                    mision = finalMision,
                    enfoque = finalEnfoque,
                    tip = finalTip,
                    audioPath = currentState.recordedAudioPath,
                    notes = currentState.typedNotes,
                    timestamp = System.currentTimeMillis()
                )
                repository.insert(entry)

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        feedbackResponse = resultFeedback,
                        showFichaSavedNotification = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Feedback communication anomaly", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "No se pudo conectar al servidor de IA para retroalimentación. Guardando localmente en tu diario."
                    )
                }
                
                // Fallback direct save
                try {
                    val entry = DiaryEntry(
                        mision = exercise.title,
                        enfoque = exercise.category,
                        tip = exercise.psychologicalTip,
                        audioPath = currentState.recordedAudioPath,
                        notes = currentState.typedNotes,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insert(entry)
                    _uiState.update {
                        it.copy(
                            feedbackResponse = "¡Misión Completada con éxito! Se archivó correctamente en tu diario personal offline de Conecta5.",
                            showFichaSavedNotification = true
                        )
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Full database storage breakdown", ex)
                }
            }
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(entry)
        }
    }

    fun startAudioPlayback(path: String, recorderHelper: AudioRecorderHelper) {
        _uiState.update {
            it.copy(playingAudioPath = path, isPlayingAudio = true)
        }
        recorderHelper.startPlayback(path) {
            _uiState.update {
                it.copy(playingAudioPath = null, isPlayingAudio = false)
            }
        }
    }

    fun stopAudioPlayback(recorderHelper: AudioRecorderHelper) {
        recorderHelper.stopPlayback()
        _uiState.update {
            it.copy(playingAudioPath = null, isPlayingAudio = false)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
