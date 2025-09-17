package com.yourapp.test.alarm

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecordingManager(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isPlayingVoice = false
    
    companion object {
        private const val TAG = "VoiceRecordingManager"
        private const val RECORDING_DIRECTORY = "alarm_voice_recordings"
    }
    
    fun startRecording(alarmId: Int): Boolean {
        return try {
            // Check if already recording
            if (isRecording) {
                Log.w(TAG, "Recording already in progress")
                return false
            }
            
            // Create recordings directory if it doesn't exist
            val recordingsDir = File(context.filesDir, RECORDING_DIRECTORY)
            if (!recordingsDir.exists()) {
                val created = recordingsDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create recordings directory")
                    return false
                }
            }
            
            // Create unique recording file for this alarm
            recordingFile = File(recordingsDir, "alarm_voice_${alarmId}_${System.currentTimeMillis()}.3gp")
            
            // Clean up any previous recorder
            stopRecording()
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(recordingFile!!.absolutePath)
                    
                    prepare()
                    start()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure MediaRecorder", e)
                    release()
                    throw e
                }
            }
            
            isRecording = true
            Log.d(TAG, "Recording started: ${recordingFile!!.absolutePath}")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission denied", e)
            stopRecording()
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording - IO error", e)
            stopRecording()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            stopRecording()
            false
        }
    }
    
    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            val filePath = recordingFile?.absolutePath
            Log.d(TAG, "Recording stopped: $filePath")
            filePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            null
        }
    }
    
    fun playRecording(filePath: String): Boolean {
        return try {
            stopPlayback() // Stop any current playback
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    isPlayingVoice = false
                    Log.d(TAG, "Playback completed")
                }
            }
            
            isPlayingVoice = true
            Log.d(TAG, "Playing recording: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play recording", e)
            false
        }
    }
    
    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying()) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPlayingVoice = false
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop playback", e)
        }
    }
    
    fun deleteRecording(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Recording deleted: $filePath, success: $deleted")
                deleted
            } else {
                Log.w(TAG, "Recording file not found: $filePath")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete recording", e)
            false
        }
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun isPlaying(): Boolean = isPlayingVoice
    
    fun getRecordingDuration(filePath: String): Int {
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }
            val duration = mp.duration
            mp.release()
            duration
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recording duration", e)
            0
        }
    }
    
    fun release() {
        stopRecording()
        stopPlayback()
    }
}
