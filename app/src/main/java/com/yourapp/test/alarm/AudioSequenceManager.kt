package com.yourapp.test.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Manages sequential audio playback for alarms with fade effects
 * Sequence: Alarm (2s fade-in + 3s full + 2s fade-out) -> TTS/Voice -> Repeat
 */
class AudioSequenceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioSequenceManager"
        private const val FADE_IN_DURATION = 2000L // 2 seconds
        private const val FULL_VOLUME_DURATION = 3000L // 3 seconds
        private const val FADE_OUT_DURATION = 2000L // 2 seconds
        private const val TOTAL_ALARM_DURATION = FADE_IN_DURATION + FULL_VOLUME_DURATION + FADE_OUT_DURATION // 7 seconds
    }
    
    // Audio players
    private var ringtonePlayer: MediaPlayer? = null
    private var voicePlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    
    // Animation and timing
    private var fadeAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sequenceRunnable: Runnable? = null
    
    // Audio settings
    private var ringtoneUri: Uri? = null
    private var ringtoneVolume: Float = 0.8f
    private var voiceRecordingPath: String? = null
    private var voiceVolume: Float = 1.0f
    private var ttsText: String? = null
    private var ttsVolume: Float = 1.0f
    private var hasTtsOverlay: Boolean = false
    private var hasVoiceOverlay: Boolean = false
    
    // State management
    private var isPlaying = false
    private var isInAlarmPhase = true
    private var sequenceStep = 0
    private var ttsInitialized = false
    
    /**
     * Initialize the audio sequence with alarm settings
     */
    fun initialize(
        ringtoneUri: Uri?,
        ringtoneVolume: Float,
        voiceRecordingPath: String?,
        voiceVolume: Float,
        ttsText: String?,
        ttsVolume: Float,
        hasVoiceOverlay: Boolean,
        hasTtsOverlay: Boolean
    ) {
        this.ringtoneUri = ringtoneUri
        this.ringtoneVolume = ringtoneVolume
        this.voiceRecordingPath = voiceRecordingPath
        this.voiceVolume = voiceVolume
        this.ttsText = ttsText
        this.ttsVolume = ttsVolume
        this.hasVoiceOverlay = hasVoiceOverlay
        this.hasTtsOverlay = hasTtsOverlay
        
        Log.d(TAG, "Audio sequence initialized - Voice: $hasVoiceOverlay, TTS: $hasTtsOverlay")
        
        // Initialize TTS if needed
        if (hasTtsOverlay && !ttsText.isNullOrBlank()) {
            initializeTts()
        }
    }
    
    /**
     * Start the audio sequence
     */
    fun startSequence() {
        if (isPlaying) {
            Log.w(TAG, "Audio sequence already playing")
            return
        }
        
        isPlaying = true
        isInAlarmPhase = true
        sequenceStep = 0
        
        Log.d(TAG, "🎵 Starting audio sequence")
        startAlarmPhase()
    }
    
    /**
     * Stop the audio sequence
     */
    fun stopSequence() {
        if (!isPlaying) return
        
        Log.d(TAG, "🛑 Stopping audio sequence")
        isPlaying = false
        
        // Stop all playback
        stopAlarmPhase()
        stopVoicePhase()
        stopTtsPhase()
        
        // Cancel any pending sequence steps
        sequenceRunnable?.let { handler.removeCallbacks(it) }
        sequenceRunnable = null
    }
    
    /**
     * Start the alarm ringtone phase with fade effects
     */
    private fun startAlarmPhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔔 Starting alarm phase (7s total)")
        isInAlarmPhase = true
        
        try {
            // Stop any existing ringtone
            stopRingtone()
            
            // Create and configure ringtone player
            ringtonePlayer = MediaPlayer().apply {
                // Set audio attributes for alarm
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                // Set data source
                val uri = ringtoneUri ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                setDataSource(context, uri)
                
                // Configure for looping during the alarm phase
                isLooping = true
                
                // Start at 0 volume for fade-in
                setVolume(0f, 0f)
                
                // Prepare and start
                prepare()
                start()
            }
            
            // Start fade-in effect
            startFadeIn()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm phase", e)
            // Skip to next phase if alarm fails
            scheduleNextPhase()
        }
    }
    
    /**
     * Create fade-in effect for alarm
     */
    private fun startFadeIn() {
        fadeAnimator?.cancel()
        
        fadeAnimator = ValueAnimator.ofFloat(0f, ringtoneVolume).apply {
            duration = FADE_IN_DURATION
            
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            
            doOnEnd {
                // After fade-in, maintain full volume for 3 seconds
                scheduleFullVolumePhase()
            }
            
            start()
        }
        
        Log.d(TAG, "📈 Started fade-in (2s)")
    }
    
    /**
     * Schedule the full volume phase
     */
    private fun scheduleFullVolumePhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔊 Full volume phase (3s)")
        
        // Ensure full volume
        ringtonePlayer?.setVolume(ringtoneVolume, ringtoneVolume)
        
        // Schedule fade-out after 3 seconds
        handler.postDelayed({
            if (isPlaying && isInAlarmPhase) {
                startFadeOut()
            }
        }, FULL_VOLUME_DURATION)
    }
    
    /**
     * Create fade-out effect for alarm
     */
    private fun startFadeOut() {
        fadeAnimator?.cancel()
        
        fadeAnimator = ValueAnimator.ofFloat(ringtoneVolume, 0f).apply {
            duration = FADE_OUT_DURATION
            
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            
            doOnEnd {
                // After fade-out, stop alarm and move to next phase
                stopRingtone()
                scheduleNextPhase()
            }
            
            start()
        }
        
        Log.d(TAG, "📉 Started fade-out (2s)")
    }
    
    /**
     * Schedule the next phase (TTS or Voice)
     */
    private fun scheduleNextPhase() {
        if (!isPlaying) return
        
        isInAlarmPhase = false
        
        // Determine which phase to play next
        when {
            hasTtsOverlay && !ttsText.isNullOrBlank() -> {
                Log.d(TAG, "🗣️ Moving to TTS phase")
                startTtsPhase()
            }
            hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty() -> {
                Log.d(TAG, "🎙️ Moving to voice phase")
                startVoicePhase()
            }
            else -> {
                Log.d(TAG, "🔄 No TTS/Voice configured, restarting alarm phase")
                // If no TTS or voice, just loop the alarm
                handler.postDelayed({
                    if (isPlaying) {
                        startAlarmPhase()
                    }
                }, 500) // Small delay before restarting
            }
        }
    }
    
    /**
     * Start the TTS phase
     */
    private fun startTtsPhase() {
        if (!isPlaying || ttsText.isNullOrBlank()) {
            scheduleAlarmRestart()
            return
        }
        
        Log.d(TAG, "🗣️ Starting TTS phase")
        
        if (!ttsInitialized) {
            Log.w(TAG, "TTS not initialized, skipping to alarm restart")
            scheduleAlarmRestart()
            return
        }
        
        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            }
            
            val result = tts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, params, "alarm_sequence_tts")
            
            if (result == TextToSpeech.SUCCESS) {
                // TTS will call onDone when finished, which will restart the alarm
                Log.d(TAG, "✅ TTS started successfully")
            } else {
                Log.e(TAG, "❌ TTS failed to start, restarting alarm")
                scheduleAlarmRestart()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS phase", e)
            scheduleAlarmRestart()
        }
    }
    
    /**
     * Start the voice recording phase
     */
    private fun startVoicePhase() {
        if (!isPlaying || voiceRecordingPath.isNullOrEmpty()) {
            scheduleAlarmRestart()
            return
        }
        
        Log.d(TAG, "🎙️ Starting voice phase")
        
        try {
            // Stop any existing voice playback
            stopVoice()
            
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                setVolume(voiceVolume, voiceVolume)
                
                setOnCompletionListener {
                    Log.d(TAG, "✅ Voice playback completed")
                    scheduleAlarmRestart()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Voice playback error: what=$what, extra=$extra")
                    scheduleAlarmRestart()
                    true
                }
                
                prepare()
                start()
            }
            
            Log.d(TAG, "✅ Voice playback started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in voice phase", e)
            scheduleAlarmRestart()
        }
    }
    
    /**
     * Schedule restart of alarm phase
     */
    private fun scheduleAlarmRestart() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔄 Scheduling alarm restart in 1 second")
        
        sequenceRunnable = Runnable {
            if (isPlaying) {
                startAlarmPhase()
            }
        }
        
        handler.postDelayed(sequenceRunnable!!, 1000) // 1 second delay before restarting alarm
    }
    
    /**
     * Initialize TTS engine
     */
    private fun initializeTts() {
        if (tts != null) return
        
        Log.d(TAG, "🗣️ Initializing TTS engine")
        
        tts = TextToSpeech(context) { status ->
            ttsInitialized = (status == TextToSpeech.SUCCESS)
            
            if (ttsInitialized) {
                Log.d(TAG, "✅ TTS initialized successfully")
                tts?.let { ttsEngine ->
                    try {
                        ttsEngine.setSpeechRate(1.0f)
                        ttsEngine.setPitch(1.0f)
                        
                        val result = ttsEngine.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "TTS language not fully supported")
                        }
                        
                        // Set completion listener to restart alarm after TTS
                        ttsEngine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "🗣️ TTS started: $utteranceId")
                            }
                            
                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "✅ TTS completed: $utteranceId")
                                if (utteranceId == "alarm_sequence_tts") {
                                    scheduleAlarmRestart()
                                }
                            }
                            
                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "❌ TTS error: $utteranceId")
                                scheduleAlarmRestart()
                            }
                        })
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error configuring TTS", e)
                        ttsInitialized = false
                    }
                }
            } else {
                Log.e(TAG, "❌ TTS initialization failed")
            }
        }
    }
    
    /**
     * Stop alarm ringtone
     */
    private fun stopAlarmPhase() {
        stopRingtone()
        fadeAnimator?.cancel()
        fadeAnimator = null
    }
    
    private fun stopRingtone() {
        try {
            ringtonePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone", e)
        }
        ringtonePlayer = null
    }
    
    /**
     * Stop voice playback
     */
    private fun stopVoicePhase() {
        stopVoice()
    }
    
    private fun stopVoice() {
        try {
            voicePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice", e)
        }
        voicePlayer = null
    }
    
    /**
     * Stop TTS
     */
    private fun stopTtsPhase() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(TAG, "🧹 Releasing audio sequence resources")
        
        stopSequence()
        
        // Release TTS
        try {
            tts?.shutdown()
            tts = null
            ttsInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
        
        // Clean up any remaining callbacks
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Check if sequence is currently playing
     */
    fun isSequencePlaying(): Boolean = isPlaying
    
    /**
     * Check if currently in alarm phase
     */
    fun isInAlarmPhase(): Boolean = isInAlarmPhase
}

// Extension function for ValueAnimator
private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
            action()
        }
    })
}