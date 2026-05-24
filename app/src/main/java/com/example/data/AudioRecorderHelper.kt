package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null

    fun startRecording(): String? {
        val cacheDir = context.cacheDir
        val audioFile = File(cacheDir, "recording_${System.currentTimeMillis()}.3gp")
        currentRecordingFile = audioFile

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
                Log.d("AudioRecorderHelper", "Recording started in ${audioFile.absolutePath}")
            }
            return audioFile.absolutePath
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Recording initialization failed", e)
            // Cleanup
            try {
                mediaRecorder?.release()
            } catch (ex: Exception) {}
            mediaRecorder = null
            return null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "stop() failed", e)
        } finally {
            mediaRecorder = null
        }
    }

    fun startPlayback(path: String, onComplete: () -> Unit) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                    stopPlayback()
                }
            } catch (e: IOException) {
                Log.e("AudioRecorderHelper", "prepare() failed for playback", e)
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "stopPlayback failed", e)
        } finally {
            mediaPlayer = null
        }
    }
}
