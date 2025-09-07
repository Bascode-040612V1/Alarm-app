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
                // Prevent back button from dismissing alarm
                // User must use snooze or dismiss buttons
                Log.d("AlarmScreenActivity", "Back button pressed - ignored for alarm screen")
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
                setDataSource(this@AlarmScreenActivity, uri)
                
                // Configure looping and volume
                isLooping = true
                setVolume(ringtoneVolume, ringtoneVolume) // CRITICAL FIX: Use user's ringtone volume setting
                
                // Prepare and start
                prepare()
                start()
                
                Log.d("AlarmScreenActivity", "Ringtone playback started with URI: $uri, Volume: $ringtoneVolume")
            }
            
            // Volume is already set to user preference - no gradual increase needed
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to start ringtone playback", e)
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
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("AlarmScreenActivity", "❌ Voice overlay error: what=$what, extra=$extra")
                        false // Return false to trigger onCompletion
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
                if (status == TextToSpeech.SUCCESS) {
                    Log.d("AlarmScreenActivity", "✅ TTS initialized successfully")
                    
                    // Configure TTS settings
                    tts?.setSpeechRate(1.0f) // Normal speech rate
                    tts?.setPitch(1.0f) // Normal pitch
                    
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
                } else {
                    Log.e("AlarmScreenActivity", "❌ Failed to initialize TTS: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ Failed to start TTS overlay", e)
        }
    }
    
    private fun startTtsLoop(params: Bundle) {
        // Create a repeating TTS loop with NO DELAY
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note: '$alarmNote'")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts")
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS immediately after current speech finishes (NO DELAY)
                            handler.postDelayed(this, 100) // Minimal 100ms delay to prevent overwhelming
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed: $result")
                        }
                    }
                }
            }
        }
        
        // Start the first TTS immediately
        handler.post(ttsRunnable)
    }
    
    private fun startTtsLoopLegacy(params: HashMap<String, String>) {
        // Create a repeating TTS loop for legacy Android
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note (legacy): '$alarmNote'")
                        @Suppress("DEPRECATION")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params)
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS after a delay (repeat every 10 seconds)
                            handler.postDelayed(this, 10000)
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed (legacy): $result")
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
        stopAlarmSound()
        stopBellAnimation()
        
        // Send snooze broadcast
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtras(intent.extras ?: Bundle())
        }
        sendBroadcast(snoozeIntent)
        
        finish()
    }

    private fun dismissAlarm() {
        stopAlarmSound()
        stopBellAnimation()
        
        // Send dismiss broadcast
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
        }
        sendBroadcast(dismissIntent)
        
        finish()
    }

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
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d("AlarmScreenActivity", "Audio focus gained - alarm has priority")
                                // Ensure our alarm sound is playing at full volume
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            }
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                // Don't stop alarm sound on focus loss - it's an alarm!
                                Log.d("AlarmScreenActivity", "Audio focus lost but keeping alarm playing")
                                // Force our alarm to continue playing
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                // Don't duck alarm sound - alarms should be at full volume
                                Log.d("AlarmScreenActivity", "Duck request ignored - alarm at full volume")
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            }
                        }
                    }
                    .build()
                
                val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
                Log.d("AlarmScreenActivity", "Audio focus request result: $result")
                
                // Force pause other media by requesting focus gain
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d("AlarmScreenActivity", "Audio focus granted - other media should pause")
                    return true
                } else {
                    Log.w("AlarmScreenActivity", "Audio focus not granted: $result")
                    return false
                }
                
            } else {
                // For older Android versions
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    { focusChange ->
                        Log.d("AlarmScreenActivity", "Audio focus changed (legacy): $focusChange")
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d("AlarmScreenActivity", "Audio focus gained (legacy)")
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            }
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                // Keep alarm playing regardless of focus changes
                                Log.d("AlarmScreenActivity", "Maintaining alarm volume despite focus change")
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                            }
                        }
                    },
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN // Use GAIN to pause other media
                )
                Log.d("AlarmScreenActivity", "Audio focus request result (legacy): $result")
                
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d("AlarmScreenActivity", "Audio focus granted (legacy) - other media should pause")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to request audio focus: ${e.message}")
            return false
        }
        return false
    }



    private fun playFallbackSound() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d("AlarmScreenActivity", "Using notification sound as fallback: $notificationUri")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmScreenActivity, notificationUri)
                prepare()
                start()
                Log.d("AlarmScreenActivity", "Playing notification sound as last resort")
            }
        } catch (finalEx: Exception) {
            Log.e("AlarmScreenActivity", "All ringtone attempts failed: ${finalEx.message}")
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
