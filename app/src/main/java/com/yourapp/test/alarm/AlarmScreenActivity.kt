package com.yourapp.test.alarm

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AlarmScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_TIME = "alarm_time"
        const val EXTRA_ALARM_TITLE = "alarm_title"
        const val EXTRA_ALARM_NOTE = "alarm_note"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_RINGTONE_NAME = "ringtone_name"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_VOICE_RECORDING_PATH = "voice_recording_path"
        const val EXTRA_HAS_VOICE_OVERLAY = "has_voice_overlay"
        const val EXTRA_HAS_TTS_OVERLAY = "has_tts_overlay"
        const val EXTRA_RINGTONE_VOLUME = "ringtone_volume"
        const val EXTRA_VOICE_VOLUME = "voice_volume"
        const val EXTRA_TTS_VOLUME = "tts_volume"
    }

    private lateinit var textTime: TextView
    private lateinit var textDate: TextView
    private lateinit var textTitle: TextView
    private lateinit var textNote: TextView
    private lateinit var imageAlarmBell: ImageView
    private lateinit var buttonSnooze: MaterialButton
    private lateinit var buttonDismiss: MaterialButton

    private var mediaPlayer: MediaPlayer? = null
    private var voiceMediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var bellAnimator: ObjectAnimator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null
    private var stopAlarmReceiver: BroadcastReceiver? = null
    private var volumeIncreaseRunnable: Runnable? = null
    private var currentVolume: Float = 0.1f // Start at 10% volume
    private val maxVolume: Float = 1.0f
    private val volumeIncreaseInterval: Long = 2000 // Increase every 2 seconds
    private val volumeIncreaseStep: Float = 0.1f // Increase by 10% each step
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var alarmId: Int = 0
    private var alarmTitle: String = ""
    private var alarmNote: String = ""
    private var ringtoneUri: Uri? = null
    private var ringtoneName: String = ""
    private var snoozeMinutes: Int = 10
    private var voiceRecordingPath: String? = null
    private var hasVoiceOverlay: Boolean = false
    private var hasTtsOverlay: Boolean = false
    private var ringtoneVolume: Float = 0.8f
    private var voiceVolume: Float = 1.0f
    private var ttsVolume: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Enhanced lock screen display - works on all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // Comprehensive window flags for maximum lock screen compatibility
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        // For Android 10+ (API 29+), additional lock screen flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        
        // For Android 8.0+ (API 26+), request keyguard dismissal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        // Legacy support for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        setContentView(R.layout.activity_alarm_screen)
        
        // Setup back button handling with modern OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent accidental back button dismissal
                Log.d("AlarmScreenActivity", "Back button pressed - showing warning")
                showBackButtonWarning()
            }
        })
        
        getIntentExtras()
        initViews()
        setupUI()
        acquireWakeLock()
        setupStopAlarmReceiver()
        playAlarmSound()
        startBellAnimation()
        startTimeUpdates()
    }

    private fun getIntentExtras() {
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE) ?: "Alarm"
        alarmNote = intent.getStringExtra(EXTRA_ALARM_NOTE) ?: ""
        ringtoneName = intent.getStringExtra(EXTRA_RINGTONE_NAME) ?: "Default"
        snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        
        val ringtoneUriString = intent.getStringExtra(EXTRA_RINGTONE_URI)
        
        // Fix: Properly handle ringtone URI - don't default to alarm if custom ringtone is selected
        ringtoneUri = if (!ringtoneUriString.isNullOrEmpty() && ringtoneUriString != "null") {
            try {
                Uri.parse(ringtoneUriString)
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Failed to parse ringtone URI: $ringtoneUriString", e)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        } else {
            // Only use default if no custom ringtone was selected
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        
        voiceRecordingPath = intent.getStringExtra(EXTRA_VOICE_RECORDING_PATH)
        hasVoiceOverlay = intent.getBooleanExtra(EXTRA_HAS_VOICE_OVERLAY, false)
        hasTtsOverlay = intent.getBooleanExtra(EXTRA_HAS_TTS_OVERLAY, false)
        ringtoneVolume = intent.getFloatExtra(EXTRA_RINGTONE_VOLUME, 0.8f)
        voiceVolume = intent.getFloatExtra(EXTRA_VOICE_VOLUME, 1.0f)
        ttsVolume = intent.getFloatExtra(EXTRA_TTS_VOLUME, 1.0f)
        
        Log.d("AlarmScreenActivity", "Alarm screen started - Title: $alarmTitle, Ringtone: $ringtoneName, URI: $ringtoneUri, RingtoneVol: $ringtoneVolume, VoiceVol: $voiceVolume, TtsVol: $ttsVolume")
    }

    private fun initViews() {
        textTime = findViewById(R.id.textTime)
        textDate = findViewById(R.id.textDate)
        textTitle = findViewById(R.id.textTitle)
        textNote = findViewById(R.id.textNote)
        imageAlarmBell = findViewById(R.id.imageAlarmBell)
        buttonSnooze = findViewById(R.id.buttonSnooze)
        buttonDismiss = findViewById(R.id.buttonDismiss)
    }

    private fun setupUI() {
        textTitle.text = alarmTitle
        
        if (alarmNote.isNotEmpty()) {
            textNote.text = alarmNote
            textNote.visibility = View.VISIBLE
        } else {
            textNote.visibility = View.GONE
        }

        buttonSnooze.text = "Snooze ($snoozeMinutes min)"
        
        buttonSnooze.setOnClickListener {
            snoozeAlarm()
        }
        
        buttonDismiss.setOnClickListener {
            dismissAlarm()
        }
        
        updateTimeAndDate()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
            PowerManager.ON_AFTER_RELEASE,
            "AlarmApp:AlarmScreenWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
    }

    private fun playAlarmSound() {
        Log.d("AlarmScreenActivity", "Starting alarm sound playback")
        
        try {
            // Stop any existing playback
            stopAlarmSound()
            
            // Request audio focus first
            if (!requestAudioFocus()) {
                Log.w("AlarmScreenActivity", "Failed to gain audio focus, playing anyway")
            }
            
            // Ensure alarm volume is at least 70% of max
            audioManager?.let { am ->
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val currentVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val minRequiredVolume = (maxVolume * 0.7).toInt()
                
                if (currentVolume < minRequiredVolume) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, minRequiredVolume, 0)
                    Log.d("AlarmScreenActivity", "Increased alarm volume to $minRequiredVolume (max: $maxVolume)")
                }
            }
            
            // Start the main ringtone
            startRingtonePlayback()
            
            // Start voice overlay if enabled and path exists
            Log.d("AlarmScreenActivity", "Voice overlay check - hasVoiceOverlay: $hasVoiceOverlay, path: $voiceRecordingPath")
            if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
                Log.d("AlarmScreenActivity", "🎙️ Starting voice overlay...")
                startVoiceOverlay()
            } else {
                Log.d("AlarmScreenActivity", "❌ Voice overlay not started - hasVoiceOverlay: $hasVoiceOverlay, path: $voiceRecordingPath")
            }
            
            // Start TTS overlay if enabled and note is not empty
            Log.d("AlarmScreenActivity", "TTS overlay check - hasTtsOverlay: $hasTtsOverlay, note: '$alarmNote'")
            if (hasTtsOverlay && alarmNote.isNotBlank()) {
                Log.d("AlarmScreenActivity", "🗣️ Starting TTS overlay...")
                startTtsOverlay()
            } else {
                Log.d("AlarmScreenActivity", "❌ TTS overlay not started - hasTtsOverlay: $hasTtsOverlay, note empty: ${alarmNote.isBlank()}")
            }
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to play alarm sound", e)
            // Try fallback sound
            playFallbackSound()
        }
    }
    
    private fun startRingtonePlayback() {
        try {
            mediaPlayer = MediaPlayer().apply {
                // Set audio attributes for alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                } else {
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                // Set data source with fallback
                val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                
                try {
                    setDataSource(this@AlarmScreenActivity, uri)
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Failed to set ringtone data source: $uri", e)
                    // Try with default alarm sound
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    setDataSource(this@AlarmScreenActivity, defaultUri)
                }
                
                // Configure looping and volume
                isLooping = true
                setVolume(ringtoneVolume, ringtoneVolume)
                
                // Add error listener
                setOnErrorListener { _, what, extra ->
                    Log.e("AlarmScreenActivity", "MediaPlayer error: what=$what, extra=$extra")
                    // Try to recover by playing fallback sound
                    try {
                        playFallbackSound()
                    } catch (fallbackEx: Exception) {
                        Log.e("AlarmScreenActivity", "Fallback sound also failed", fallbackEx)
                    }
                    true // Indicate we handled the error
                }
                
                // Prepare and start with error handling
                try {
                    prepare()
                    start()
                    Log.d("AlarmScreenActivity", "Ringtone playback started with URI: $uri, Volume: $ringtoneVolume")
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Failed to prepare/start ringtone", e)
                    playFallbackSound()
                }
            }
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to create MediaPlayer for ringtone", e)
            playFallbackSound()
        }
    }
    
    private fun startVoiceOverlay() {
        try {
            voiceRecordingPath?.let { path ->
                Log.d("AlarmScreenActivity", "🎙️ Attempting to start voice overlay from: $path")
                
                // Check if file exists
                val file = java.io.File(path)
                if (!file.exists()) {
                    Log.e("AlarmScreenActivity", "❌ Voice recording file not found: $path")
                    return
                }
                
                voiceMediaPlayer = MediaPlayer().apply {
                    // Set audio attributes for voice overlay
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                    } else {
                        setAudioStreamType(AudioManager.STREAM_ALARM)
                    }
                    
                    setDataSource(path)
                    setVolume(voiceVolume, voiceVolume) // Use user-selected voice volume
                    
                    // Loop the voice recording continuously
                    isLooping = true
                    
                    setOnPreparedListener {
                        Log.d("AlarmScreenActivity", "✅ Voice overlay prepared, starting playback...")
                        start()
                    }
                    
                    // Add error listener first
                    setOnErrorListener { _, what, extra ->
                        Log.e("AlarmScreenActivity", "❌ Voice overlay error: what=$what, extra=$extra")
                        // Clean up on error
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e("AlarmScreenActivity", "Error releasing voice MediaPlayer", e)
                        }
                        voiceMediaPlayer = null
                        false // Let the system handle the error
                    }
                    
                    prepareAsync() // Use async to avoid blocking
                    
                    Log.d("AlarmScreenActivity", "🎙️ Voice overlay MediaPlayer configured")
                }
            } ?: run {
                Log.e("AlarmScreenActivity", "❌ Voice recording path is null")
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ Failed to start voice overlay", e)
            // Continue without voice overlay if it fails
        }
    }

    private fun startTtsOverlay() {
        try {
            if (alarmNote.isBlank()) {
                Log.w("AlarmScreenActivity", "❌ TTS overlay requested but note is empty")
                return
            }
            
            Log.d("AlarmScreenActivity", "🗣️ Initializing TTS for note: '$alarmNote'")
            
            tts = TextToSpeech(this) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        Log.d("AlarmScreenActivity", "✅ TTS initialized successfully")
                        
                        // Configure TTS settings with error handling
                        tts?.let { ttsEngine ->
                            try {
                                ttsEngine.setSpeechRate(1.0f) // Normal speech rate
                                ttsEngine.setPitch(1.0f) // Normal pitch
                                
                                // Check if TTS is available
                                val result = ttsEngine.setLanguage(Locale.getDefault())
                                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    Log.w("AlarmScreenActivity", "TTS language not supported, using default")
                                }
                                
                                // Set audio attributes for TTS to use alarm stream
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    val params = Bundle()
                                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                                    
                                    // Start TTS with looping
                                    startTtsLoop(params)
                                } else {
                                    // For older Android versions
                                    val params = HashMap<String, String>()
                                    params[TextToSpeech.Engine.KEY_PARAM_STREAM] = AudioManager.STREAM_ALARM.toString()
                                    params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = ttsVolume.toString()
                                    
                                    // Start TTS with looping
                                    startTtsLoopLegacy(params)
                                }
                            } catch (e: Exception) {
                                Log.e("AlarmScreenActivity", "Error configuring TTS", e)
                            }
                        }
                    } else {
                        Log.e("AlarmScreenActivity", "❌ Failed to initialize TTS: $status")
                    }
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Error in TTS initialization callback", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ Failed to start TTS overlay", e)
        }
    }
    
    private fun startTtsLoop(params: Bundle) {
        // Create a repeating TTS loop with 1 second delays between repetitions
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note: '$alarmNote'")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts")
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS after 1 second for faster looping
                            handler.postDelayed(this, 1000) // 1 second delay between TTS repetitions
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed: $result")
                            // Retry after 2 seconds if TTS fails
                            handler.postDelayed(this, 2000)
                        }
                    }
                }
            }
        }
        
        // Start the first TTS immediately
        handler.post(ttsRunnable)
    }
    
    private fun startTtsLoopLegacy(params: HashMap<String, String>) {
        // Create a repeating TTS loop for legacy Android with 1 second delays
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note (legacy): '$alarmNote'")
                        @Suppress("DEPRECATION")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params)
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS after 1 second for faster looping
                            handler.postDelayed(this, 1000)
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed (legacy): $result")
                            // Retry after 2 seconds if TTS fails
                            handler.postDelayed(this, 2000)
                        }
                    }
                }
            }
        }
        
        // Start the first TTS immediately
        handler.post(ttsRunnable)
    }

    private fun startBellAnimation() {
        bellAnimator = ObjectAnimator.ofFloat(imageAlarmBell, "rotation", -30f, 30f).apply {
            duration = 200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun startTimeUpdates() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                updateTimeAndDate()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeUpdateRunnable!!)
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        
        textTime.text = timeFormat.format(calendar.time)
        textDate.text = dateFormat.format(calendar.time)
    }

    private fun snoozeAlarm() {
        Log.d("AlarmScreenActivity", "Snoozing alarm - ID: $alarmId")
        
        // Stop all audio immediately
        stopAlarmSound()
        stopBellAnimation()
        
        // Create comprehensive snooze intent with all original data
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            
            // Preserve ALL original alarm data to prevent data loss
            putExtra("ALARM_TIME", intent.getStringExtra(AlarmScreenActivity.EXTRA_ALARM_TIME))
            putExtra("ALARM_TITLE", alarmTitle)
            putExtra("ALARM_NOTE", alarmNote)
            putExtra("RINGTONE_URI", intent.getStringExtra(AlarmScreenActivity.EXTRA_RINGTONE_URI))
            putExtra("RINGTONE_NAME", ringtoneName)
            putExtra("VOICE_RECORDING_PATH", voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", hasTtsOverlay)
            putExtra("RINGTONE_VOLUME", ringtoneVolume)
            putExtra("VOICE_VOLUME", voiceVolume)
            putExtra("TTS_VOLUME", ttsVolume)
            
            // Add reliability flags
            putExtra("IS_SNOOZE_ACTION", true)
            putExtra("SNOOZE_TIMESTAMP", System.currentTimeMillis())
        }
        
        try {
            sendBroadcast(snoozeIntent)
            Log.d("AlarmScreenActivity", "Snooze broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to send snooze broadcast: ${e.message}")
            // Show error to user
            showSnoozeError()
            return
        }
        
        // Finish activity with delay to ensure broadcast is processed
        handler.postDelayed({
            finish()
        }, 100)
    }
    
    private fun dismissAlarm() {
        Log.d("AlarmScreenActivity", "Dismissing alarm - ID: $alarmId")
        
        // Stop all audio immediately
        stopAlarmSound()
        stopBellAnimation()
        
        // Create dismiss intent
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
            putExtra("DISMISS_TIMESTAMP", System.currentTimeMillis())
        }
        
        try {
            sendBroadcast(dismissIntent)
            Log.d("AlarmScreenActivity", "Dismiss broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to send dismiss broadcast: ${e.message}")
        }
        
        // Finish activity immediately for dismiss
        finish()
    }
    
    /**
     * Shows an error message when snooze fails
     */
    private fun showSnoozeError() {
        try {
            // You could show a Toast or update UI to indicate snooze failed
            runOnUiThread {
                // For now, just log and continue - in a real app you might show UI feedback
                Log.e("AlarmScreenActivity", "Snooze operation failed - user should be notified")
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error showing snooze error: ${e.message}")
        }
    }
    
    /**
     * Shows warning when user tries to use back button
     */
    private fun showBackButtonWarning() {
        // Temporarily disable the warning and just prevent back button usage
        Log.d("AlarmScreenActivity", "Back button blocked - user must use snooze/dismiss buttons")
        
        // Optional: You could show a brief visual indication that back button is disabled
        // For now, we just log and ignore the back press
    }
    
    /**
     * Enhanced cleanup when stopping alarm sounds
     */

    private fun stopAlarmSound() {
        Log.d("AlarmScreenActivity", "Stopping alarm sound...")
        
        // Stop and release ringtone MediaPlayer
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Error stopping ringtone: ${e.message}")
            }
        }
        mediaPlayer = null
        
        // Stop and release voice overlay MediaPlayer
        voiceMediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Error stopping voice overlay: ${e.message}")
            }
        }
        voiceMediaPlayer = null
        
        // Stop and release TTS
        tts?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Error stopping TTS: ${e.message}")
            }
        }
        tts = null
        
        // Stop all handler callbacks to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
        
        // Stop volume increase runnable
        volumeIncreaseRunnable?.let { handler.removeCallbacks(it) }
        volumeIncreaseRunnable = null
        
        Log.d("AlarmScreenActivity", "✅ All alarm sounds stopped")
    }

    private fun stopBellAnimation() {
        bellAnimator?.cancel()
        imageAlarmBell.rotation = 0f
    }

    private fun setupStopAlarmReceiver() {
        stopAlarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.yourapp.test.alarm.STOP_ALARM") {
                    val receivedAlarmId = intent.getIntExtra("ALARM_ID", -1)
                    if (receivedAlarmId == alarmId || receivedAlarmId == -1) {
                        Log.d("AlarmScreenActivity", "Received stop alarm broadcast")
                        finish()
                    }
                }
            }
        }
        
        val filter = IntentFilter("com.yourapp.test.alarm.STOP_ALARM")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopAlarmReceiver, filter)
        }
        
        // Also handle the STOP_ALARM action from intent
        if (intent?.action == "STOP_ALARM") {
            Log.d("AlarmScreenActivity", "Received stop alarm intent")
            finish()
        }
    }

    private fun requestAudioFocus(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android 8.0+ - Use AUDIOFOCUS_GAIN to pause other media
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false) // Don't duck, we want full focus
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d("AlarmScreenActivity", "Audio focus changed: $focusChange")
                        // For alarms, we maintain volume regardless of focus changes
                        // This ensures alarms are always audible
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d("AlarmScreenActivity", "Audio focus gained")
                            }
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                // Keep alarm playing - alarms should not be interrupted
                                Log.d("AlarmScreenActivity", "Audio focus lost but keeping alarm active")
                            }
                        }
                    }
                    .build()
                
                val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
                Log.d("AlarmScreenActivity", "Audio focus request result: $result")
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                
            } else {
                // For older Android versions - simplified approach
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    { focusChange ->
                        Log.d("AlarmScreenActivity", "Audio focus changed (legacy): $focusChange")
                        // Keep alarm playing regardless of focus changes
                    },
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d("AlarmScreenActivity", "Audio focus request result (legacy): $result")
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to request audio focus: ${e.message}")
            return false
        }
    }



    private fun playFallbackSound() {
        try {
            // Try notification sound first as it's more likely to be available
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d("AlarmScreenActivity", "Using notification sound as fallback: $notificationUri")
            
            mediaPlayer = MediaPlayer().apply {
                try {
                    // Set audio attributes for alarm
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        setAudioStreamType(AudioManager.STREAM_ALARM)
                    }
                    
                    setDataSource(this@AlarmScreenActivity, notificationUri)
                    isLooping = true
                    prepare()
                    start()
                    Log.d("AlarmScreenActivity", "Fallback notification sound started successfully")
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Notification fallback failed, trying ringtone", e)
                    
                    try {
                        // Try ringtone as second fallback
                        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        reset()
                        setDataSource(this@AlarmScreenActivity, ringtoneUri)
                        prepare()
                        start()
                        Log.d("AlarmScreenActivity", "Fallback ringtone sound started")
                    } catch (e2: Exception) {
                        Log.e("AlarmScreenActivity", "All fallback sounds failed", e2)
                        // If all sounds fail, at least ensure vibration works if enabled
                        release()
                        mediaPlayer = null
                    }
                }
            }
        } catch (finalEx: Exception) {
            Log.e("AlarmScreenActivity", "Complete fallback failure: ${finalEx.message}")
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        stopBellAnimation()
        timeUpdateRunnable?.let { handler.removeCallbacks(it) }
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // Release audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager?.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error releasing audio focus: ${e.message}")
        }
        
        // Unregister stop alarm receiver
        stopAlarmReceiver?.let { unregisterReceiver(it) }
    }


}
