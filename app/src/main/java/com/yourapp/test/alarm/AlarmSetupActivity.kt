package com.yourapp.test.alarm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AlarmSetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ITEM = "alarm_item"
        const val EXTRA_IS_EDIT_MODE = "is_edit_mode"
        const val RESULT_ALARM_SAVED = Activity.RESULT_OK
        const val RESULT_ALARM_DELETED = Activity.RESULT_FIRST_USER + 1
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    private lateinit var timePicker: TimePicker
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextNote: TextInputEditText
    private lateinit var chipSunday: Chip
    private lateinit var chipMonday: Chip
    private lateinit var chipTuesday: Chip
    private lateinit var chipWednesday: Chip
    private lateinit var chipThursday: Chip
    private lateinit var chipFriday: Chip
    private lateinit var chipSaturday: Chip
    private lateinit var textSelectedRingtone: TextView
    private lateinit var spinnerSnooze: Spinner
    private lateinit var layoutRingtoneSelector: LinearLayout
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var buttonRecordVoice: Button
    private lateinit var buttonPlayVoice: Button
    private lateinit var buttonDeleteVoice: Button
    private lateinit var switchVoiceOverlay: Switch
    private lateinit var textVoiceStatus: TextView
    private lateinit var seekBarRingtoneVolume: SeekBar
    private lateinit var seekBarVoiceVolume: SeekBar
    private lateinit var textRingtoneVolume: TextView
    private lateinit var textVoiceVolume: TextView
    private lateinit var buttonTestTts: Button
    private lateinit var switchTtsOverlay: Switch
    private lateinit var buttonEveryday: Button
    private lateinit var toggleVoiceHistory: ToggleButton
    private lateinit var switchVibration: Switch
    private lateinit var seekBarTtsVolume: SeekBar
    private lateinit var textTtsVolume: TextView

    private var selectedRingtoneUri: Uri? = null
    private var selectedRingtoneName: String = "Default"
    private var existingAlarm: AlarmItem? = null
    private var isEditMode: Boolean = false
    
    // Voice Recording Variables
    private lateinit var voiceRecordingManager: VoiceRecordingManager
    private var currentVoiceRecordingPath: String? = null
    private var isVoiceOverlayEnabled: Boolean = false
    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingStartTime: Long = 0
    
    // CRITICAL FIX: Add missing handler for volume slider delays
    private val handler = Handler(Looper.getMainLooper())
    
    // TTS Variables
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var isTtsOverlayEnabled: Boolean = false
    private var ttsVolume: Float = 1.0f // Default 100%
    
    // Volume Settings
    private var ringtoneVolume: Float = 0.8f // Default 80%
    private var voiceVolume: Float = 1.0f // Default 100%
    
    // Vibration Setting
    private var hasVibration: Boolean = true // Default enabled
    
    // Sound Preview Variables
    private var ringtonePreviewPlayer: MediaPlayer? = null
    private var voicePreviewPlayer: MediaPlayer? = null

    private lateinit var dayChips: Map<Int, Chip>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_setup)

        // Get data from intent
        existingAlarm = intent.getParcelableExtra(EXTRA_ALARM_ITEM)
        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        initViews()
        setupToolbar()
        setupTimePicker()
        setupSnoozeSpinner()
        setupRingtoneSelector()
        setupButtons()
        setupVoiceRecording()
        setupTts()
        setupNewUIElements()
        setupVolumeControls()

        existingAlarm?.let { populateExistingAlarm(it) }

        // Request record audio permission
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
    }

    private fun initViews() {
        timePicker = findViewById(R.id.timePicker)
        editTextTitle = findViewById(R.id.editTextTitle)
        editTextNote = findViewById(R.id.editTextNote)
        chipSunday = findViewById(R.id.chipSunday)
        chipMonday = findViewById(R.id.chipMonday)
        chipTuesday = findViewById(R.id.chipTuesday)
        chipWednesday = findViewById(R.id.chipWednesday)
        chipThursday = findViewById(R.id.chipThursday)
        chipFriday = findViewById(R.id.chipFriday)
        chipSaturday = findViewById(R.id.chipSaturday)
        textSelectedRingtone = findViewById(R.id.textSelectedRingtone)
        spinnerSnooze = findViewById(R.id.spinnerSnooze)
        layoutRingtoneSelector = findViewById(R.id.layoutRingtoneSelector)
        buttonSave = findViewById(R.id.buttonSave)
        buttonDelete = findViewById(R.id.buttonDelete)
        buttonRecordVoice = findViewById(R.id.buttonRecordVoice)
        buttonPlayVoice = findViewById(R.id.buttonPlayVoice)
        buttonDeleteVoice = findViewById(R.id.buttonDeleteVoice)
        switchVoiceOverlay = findViewById(R.id.switchVoiceOverlay)
        textVoiceStatus = findViewById(R.id.textVoiceStatus)
        buttonTestTts = findViewById(R.id.buttonTestTts)
        switchTtsOverlay = findViewById(R.id.switchTtsOverlay)
        buttonEveryday = findViewById(R.id.buttonEveryday)
        toggleVoiceHistory = findViewById(R.id.toggleVoiceHistory)
        switchVibration = findViewById(R.id.switchVibration)
        
        // Volume Controls
        seekBarRingtoneVolume = findViewById(R.id.seekBarRingtoneVolume)
        seekBarVoiceVolume = findViewById(R.id.seekBarVoiceVolume)
        textRingtoneVolume = findViewById(R.id.textRingtoneVolume)
        textVoiceVolume = findViewById(R.id.textVoiceVolume)
        seekBarTtsVolume = findViewById(R.id.seekBarTtsVolume)
        textTtsVolume = findViewById(R.id.textTtsVolume)
        
        seekBarTtsVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    ttsVolume = progress / 100.0f
                    textTtsVolume.text = "$progress%"
                    Log.d("AlarmSetupActivity", "🔊 TTS volume changed to: $progress%")
                    
                    // Play a brief TTS sample to preview the volume level
                    if (ttsInitialized) {
                        tts?.setSpeechRate(1.0f)
                        tts?.setPitch(1.0f)
                        
                        // Use Bundle parameters for TTS volume
                        val params = Bundle()
                        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                        
                        tts?.speak("Hello, this is a test of the TTS volume.", TextToSpeech.QUEUE_FLUSH, params, "test_tts")
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Set initial values
        seekBarTtsVolume.progress = (ttsVolume * 100).toInt()
        textTtsVolume.text = "${(ttsVolume * 100).toInt()}%"
        
        // Setup Ringtone Volume Slider with Sound Preview
        seekBarRingtoneVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    ringtoneVolume = progress / 100.0f
                    textRingtoneVolume.text = "$progress%"
                    Log.d("AlarmSetupActivity", "🔔 Ringtone volume changed to: $progress%")
                    
                    // Play a brief ringtone sample to preview the volume level
                    playRingtonePreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Stop preview when user stops adjusting
                stopRingtonePreview()
            }
        })
        
        // Setup Voice Volume Slider with Sound Preview
        seekBarVoiceVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    voiceVolume = progress / 100.0f
                    textVoiceVolume.text = "$progress%"
                    Log.d("AlarmSetupActivity", "🎙️ Voice volume changed to: $progress%")
                    
                    // Play a brief voice sample to preview the volume level
                    if (currentVoiceRecordingPath != null) {
                        playVoicePreview()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Stop any existing preview when user starts adjusting
                stopAllPreviews()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Stop preview when user finishes adjusting
                handler.postDelayed({ stopAllPreviews() }, 2000) // Stop after 2 seconds
            }
        })
        
        // Set initial values for all volume controls
        seekBarRingtoneVolume.progress = (ringtoneVolume * 100).toInt()
        textRingtoneVolume.text = "${(ringtoneVolume * 100).toInt()}%"
        seekBarVoiceVolume.progress = (voiceVolume * 100).toInt()
        textVoiceVolume.text = "${(voiceVolume * 100).toInt()}%"
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = if (isEditMode) "Edit Alarm" else "Add Alarm"
        }

        // Show delete button only in edit mode
        buttonDelete.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupTimePicker() {
        // Set TimePicker to use system's 12/24-hour format
        timePicker.setIs24HourView(DateFormat.is24HourFormat(this))
    }

    private fun setupSnoozeSpinner() {
        val snoozeOptions = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "25 minutes", "30 minutes")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, snoozeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSnooze.adapter = adapter
        spinnerSnooze.setSelection(1) // Default to 10 minutes
    }

    private fun setupRingtoneSelector() {
        layoutRingtoneSelector.setOnClickListener {
            showRingtoneSelectionDialog()
        }
    }

    private fun showRingtoneSelectionDialog() {
        val ringtoneManager = RingtoneManager(this)
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
        
        val cursor = ringtoneManager.cursor
        val ringtoneNames = mutableListOf<String>()
        val ringtoneUris = mutableListOf<Uri?>()
        
        // Add default option with proper URI
        ringtoneNames.add("Default")
        ringtoneUris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        
        // Add system ringtones
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = ringtoneManager.getRingtoneUri(cursor.position)
            ringtoneNames.add(title)
            ringtoneUris.add(uri)
        }
        cursor.close()
        
        // Add custom ringtones if no system ringtones found
        if (ringtoneNames.size == 1) {
            val customRingtones = arrayOf("Beep Beep", "Rooster", "Gentle Wake", "Classic Bell", "Digital Alarm")
            ringtoneNames.addAll(customRingtones)
            customRingtones.forEach { 
                ringtoneUris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Ringtone")
            .setItems(ringtoneNames.toTypedArray()) { _, which ->
                selectedRingtoneName = ringtoneNames[which]
                selectedRingtoneUri = ringtoneUris[which]
                textSelectedRingtone.text = selectedRingtoneName
                
                Log.d("AlarmSetupActivity", "Ringtone selected: $selectedRingtoneName, URI: $selectedRingtoneUri")
                
                // Preview the selected ringtone
                ringtoneUris[which]?.let { uri ->
                    try {
                        val ringtone = RingtoneManager.getRingtone(this, uri)
                        ringtone?.play()
                        // Stop after 2 seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            ringtone?.stop()
                        }, 2000)
                    } catch (e: Exception) {
                        Log.e("AlarmSetupActivity", "Failed to preview ringtone: ${e.message}")
                    }
                }
            }
            .show()
    }

    private fun setupButtons() {
        // Initialize dayChips map for Everyday button functionality
        dayChips = mapOf(
            Calendar.SUNDAY to chipSunday,
            Calendar.MONDAY to chipMonday,
            Calendar.TUESDAY to chipTuesday,
            Calendar.WEDNESDAY to chipWednesday,
            Calendar.THURSDAY to chipThursday,
            Calendar.FRIDAY to chipFriday,
            Calendar.SATURDAY to chipSaturday
        )
        
        buttonSave.setOnClickListener { saveAlarm() }

        buttonDelete.setOnClickListener {
            deleteAlarm()
        }
    }

    private fun setupVoiceRecording() {
        Log.d("AlarmSetupActivity", "🎙️ Setting up voice recording...")
        
        // Initialize VoiceRecordingManager
        voiceRecordingManager = VoiceRecordingManager(this)
        
        // Simple Record button - just test if it works
        buttonRecordVoice.setOnClickListener {
            Log.d("AlarmSetupActivity", "🎙️ RECORD BUTTON CLICKED!")
            Toast.makeText(this, "🎙️ Record button works!", Toast.LENGTH_SHORT).show()
            
            // Check if we have permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                Toast.makeText(this, "Requesting microphone permission...", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
                return@setOnClickListener
            }
            
            // Toggle recording
            if (voiceRecordingManager.isRecording()) {
                // Stop recording
                val path = voiceRecordingManager.stopRecording()
                if (path != null) {
                    currentVoiceRecordingPath = path
                    buttonRecordVoice.text = "🎙️ Record"
                    buttonPlayVoice.isEnabled = true
                    buttonDeleteVoice.isEnabled = true
                    switchVoiceOverlay.isEnabled = true
                    textVoiceStatus.text = "✅ Voice recorded!"
                    updateVolumeControlsState() // Enable voice volume control
                    Toast.makeText(this, "✅ Recording saved!", Toast.LENGTH_SHORT).show()
                } else {
                    textVoiceStatus.text = "❌ Recording failed"
                    Toast.makeText(this, "❌ Failed to save recording", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Start recording
                val alarmId = existingAlarm?.id ?: System.currentTimeMillis().toInt()
                if (voiceRecordingManager.startRecording(alarmId)) {
                    buttonRecordVoice.text = "⏹️ Stop Recording"
                    textVoiceStatus.text = "🔴 Recording..."
                    Toast.makeText(this, "🔴 Recording started!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Failed to start recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Simple Play button
        buttonPlayVoice.setOnClickListener {
            Log.d("AlarmSetupActivity", "▶️ PLAY BUTTON CLICKED!")
            Toast.makeText(this, "▶️ Play button works!", Toast.LENGTH_SHORT).show()
            
            if (currentVoiceRecordingPath == null) {
                Toast.makeText(this, "❌ No recording to play", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (voiceRecordingManager.isPlaying()) {
                // Stop playback
                voiceRecordingManager.stopPlayback()
                buttonPlayVoice.text = "▶️ Play"
                textVoiceStatus.text = "✅ Voice recorded"
                Toast.makeText(this, "⏸️ Playback stopped", Toast.LENGTH_SHORT).show()
            } else {
                // Start playback
                if (voiceRecordingManager.playRecording(currentVoiceRecordingPath!!)) {
                    buttonPlayVoice.text = "⏸️ Stop"
                    textVoiceStatus.text = "🔊 Playing..."
                    Toast.makeText(this, "🔊 Playing recording", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Failed to play recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Simple Delete button
        buttonDeleteVoice.setOnClickListener {
            Log.d("AlarmSetupActivity", "🗑️ DELETE BUTTON CLICKED!")
            Toast.makeText(this, "🗑️ Delete button works!", Toast.LENGTH_SHORT).show()
            
            if (currentVoiceRecordingPath == null) {
                Toast.makeText(this, "❌ No recording to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (voiceRecordingManager.deleteRecording(currentVoiceRecordingPath!!)) {
                currentVoiceRecordingPath = null
                isVoiceOverlayEnabled = false
                buttonPlayVoice.isEnabled = false
                buttonDeleteVoice.isEnabled = false
                switchVoiceOverlay.isEnabled = false
                switchVoiceOverlay.isChecked = false
                textVoiceStatus.text = "No voice recording"
                updateVolumeControlsState() // Disable voice volume control
                Toast.makeText(this, "🗑️ Recording deleted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Failed to delete recording", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Voice Overlay switch with STRICT mutual exclusivity
        switchVoiceOverlay.setOnCheckedChangeListener { _, isChecked ->
            Log.d("AlarmSetupActivity", "🔛 Voice Overlay SWITCH CHANGED: $isChecked")
            isVoiceOverlayEnabled = isChecked
            
            if (isChecked) {
                // STRICTLY disable TTS when Voice overlay is enabled
                if (isTtsOverlayEnabled) {
                    isTtsOverlayEnabled = false
                    switchTtsOverlay.isChecked = false
                    Toast.makeText(this, "🎙️ Voice overlay enabled - TTS automatically disabled", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "🎙️ Voice overlay enabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "🎙️ Voice overlay disabled", Toast.LENGTH_SHORT).show()
            }
            
            textVoiceStatus.text = if (isChecked && currentVoiceRecordingPath != null) {
                "✅ Voice overlay enabled"
            } else if (currentVoiceRecordingPath != null) {
                "✅ Voice recorded"
            } else {
                "No voice recording"
            }
            
            // Update volume controls when voice overlay changes
            updateVolumeControlsState()
        }
        
        Log.d("AlarmSetupActivity", "✅ Voice recording setup completed!")
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
            if (ttsInitialized) {
                Log.d("AlarmSetupActivity", "🔊 TTS initialized successfully")
                updateTtsAvailability()
            } else {
                Log.e("AlarmSetupActivity", "❌ Failed to initialize TTS")
            }
        }
        
        // Setup TTS Test button
        buttonTestTts.setOnClickListener {
            val noteText = editTextNote.text?.toString()?.trim()
            if (noteText.isNullOrEmpty()) {
                Toast.makeText(this, "❌ Enter a note to test TTS", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (!ttsInitialized) {
                Toast.makeText(this, "❌ TTS not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Test TTS with current note from user input
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            
            // Use Bundle parameters for TTS volume
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            
            val result = tts?.speak(noteText, TextToSpeech.QUEUE_FLUSH, params, "test_tts")
            if (result == TextToSpeech.SUCCESS) {
                Toast.makeText(this, "🔊 Testing TTS with your note...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ TTS test failed", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup TTS Enable switch with STRICT mutual exclusivity
        switchTtsOverlay.setOnCheckedChangeListener { _, isChecked ->
            isTtsOverlayEnabled = isChecked
            if (isChecked) {
                // STRICTLY disable voice overlay when TTS is enabled
                if (isVoiceOverlayEnabled) {
                    isVoiceOverlayEnabled = false
                    switchVoiceOverlay.isChecked = false
                    Toast.makeText(this, "🔊 TTS enabled - Voice overlay automatically disabled", Toast.LENGTH_LONG).show()
                }
            }
            updateVolumeControlsState()
            Log.d("AlarmSetupActivity", "🔊 TTS overlay enabled: $isChecked")
        }
        
        // Monitor note text changes to update TTS availability
        editTextNote.setOnFocusChangeListener { _, _ ->
            updateTtsAvailability()
        }
        
        // Update TTS availability initially
        updateTtsAvailability()
    }
    
    private fun updateTtsAvailability() {
        val noteText = editTextNote.text?.toString()?.trim()
        val hasNote = !noteText.isNullOrEmpty()
        
        if (hasNote && ttsInitialized) {
            // Enable all TTS controls when note exists and TTS is ready
            buttonTestTts.isEnabled = true
            switchTtsOverlay.isEnabled = true
            
        } else if (!hasNote) {
            // Disable TTS controls when no note is available
            buttonTestTts.isEnabled = false
            switchTtsOverlay.isEnabled = false
            switchTtsOverlay.isChecked = false
            isTtsOverlayEnabled = false
            
        } else {
            // TTS is initializing
            buttonTestTts.isEnabled = false
            switchTtsOverlay.isEnabled = false
        }
        
        // Update volume controls when TTS availability changes
        updateVolumeControlsState()
    }

    private fun setupNewUIElements() {
        Log.d("AlarmSetupActivity", "🔘 Setting up new UI elements...")
        
        // Setup Everyday button with TOGGLE functionality
        buttonEveryday.setOnClickListener {
            Log.d("AlarmSetupActivity", "📅 Everyday button clicked")
            
            // Check if all days are currently selected
            val allSelected = dayChips.values.all { it.isChecked }
            
            if (allSelected) {
                // If all days are selected, deselect all
                dayChips.values.forEach { chip ->
                    chip.isChecked = false
                }
                Toast.makeText(this, "📅 All days deselected", Toast.LENGTH_SHORT).show()
            } else {
                // If not all days are selected, select all
                dayChips.values.forEach { chip ->
                    chip.isChecked = true
                }
                Toast.makeText(this, "📅 All days selected", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup Vibration switch
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            hasVibration = isChecked
            Log.d("AlarmSetupActivity", "📳 Vibration enabled: $isChecked")
            Toast.makeText(this, if (isChecked) "📳 Vibration enabled" else "📳 Vibration disabled", Toast.LENGTH_SHORT).show()
        }
        
        // CRITICAL FIX: Add missing Voice History Toggle Button functionality
        toggleVoiceHistory.setOnCheckedChangeListener { _, isChecked ->
            Log.d("AlarmSetupActivity", "📂 Voice History Toggle: $isChecked")
            if (isChecked) {
                // Show voice recording history dialog
                showVoiceHistoryDialog()
                Toast.makeText(this, "📂 Showing voice recording history", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "📂 Voice history hidden", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup Voice History Toggle Button
        toggleVoiceHistory.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d("AlarmSetupActivity", "📂 Show Voice History toggled ON")
                showVoiceHistoryDialog()
            } else {
                Log.d("AlarmSetupActivity", "📂 Voice History toggled OFF")
                // Toggle will automatically change text to "Show History"
            }
        }
        
        Log.d("AlarmSetupActivity", "✅ New UI elements setup completed!")
    }

    private fun setupVolumeControls() {
        Log.d("AlarmSetupActivity", "🔊 Setting up volume controls...")
        
        // Setup Ringtone Volume Control
        seekBarRingtoneVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    ringtoneVolume = progress / 100.0f
                    textRingtoneVolume.text = "$progress%"
                    Log.d("AlarmSetupActivity", "🔔 Ringtone volume changed to: $progress%")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup Voice Volume Control
        seekBarVoiceVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    voiceVolume = progress / 100.0f
                    textVoiceVolume.text = "$progress%"
                    Log.d("AlarmSetupActivity", "🎙️ Voice volume changed to: $progress%")
                    
                    // CRITICAL FIX: Play voice preview at new volume level
                    if (currentVoiceRecordingPath != null) {
                        playVoicePreview()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Stop any existing preview when user starts adjusting
                stopAllPreviews()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Stop preview when user finishes adjusting
                handler.postDelayed({ stopAllPreviews() }, 2000) // Stop after 2 seconds
            }
        })
        
        // Set initial values
        seekBarRingtoneVolume.progress = (ringtoneVolume * 100).toInt()
        seekBarVoiceVolume.progress = (voiceVolume * 100).toInt()
        textRingtoneVolume.text = "${(ringtoneVolume * 100).toInt()}%"
        textVoiceVolume.text = "${(voiceVolume * 100).toInt()}%"
        
        // Enable voice volume control only when voice recording exists
        updateVolumeControlsState()
        
        Log.d("AlarmSetupActivity", "✅ Volume controls setup completed!")
    }
    
    private fun updateVolumeControlsState() {
        // Enable voice volume control only when voice recording exists
        val hasVoiceRecording = currentVoiceRecordingPath != null
        seekBarVoiceVolume.isEnabled = hasVoiceRecording && isVoiceOverlayEnabled
        textVoiceVolume.alpha = if (hasVoiceRecording && isVoiceOverlayEnabled) 1.0f else 0.5f
        
        // Enable TTS volume control only when TTS is enabled
        seekBarTtsVolume.isEnabled = isTtsOverlayEnabled
        textTtsVolume.alpha = if (isTtsOverlayEnabled) 1.0f else 0.5f
        
        Log.d("AlarmSetupActivity", "Volume controls state updated - Voice enabled: $hasVoiceRecording, TTS enabled: $isTtsOverlayEnabled")
    }

    private fun populateExistingAlarm(alarm: AlarmItem) {
        // Set time
        val calendar = alarm.calendar
        timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
        timePicker.minute = calendar.get(Calendar.MINUTE)

        // Set title and note
        editTextTitle.setText(alarm.title)
        editTextNote.setText(alarm.note)

        // Set repeat days
        dayChips.forEach { (day, chip) ->
            chip.isChecked = alarm.repeatDays.contains(day)
        }

        // Set ringtone
        selectedRingtoneUri = alarm.ringtoneUri
        selectedRingtoneName = alarm.ringtoneName
        textSelectedRingtone.text = selectedRingtoneName

        // Set snooze
        val snoozeOptions = arrayOf(5, 10, 15, 20, 25, 30)
        val snoozeIndex = snoozeOptions.indexOf(alarm.snoozeMinutes)
        if (snoozeIndex >= 0) {
            spinnerSnooze.setSelection(snoozeIndex)
        }

        // Set voice recording
        currentVoiceRecordingPath = alarm.voiceRecordingPath
        isVoiceOverlayEnabled = alarm.hasVoiceOverlay
        
        if (currentVoiceRecordingPath != null) {
            buttonPlayVoice.isEnabled = true
            buttonDeleteVoice.isEnabled = true
            switchVoiceOverlay.isEnabled = true
            switchVoiceOverlay.isChecked = isVoiceOverlayEnabled
            textVoiceStatus.text = if (isVoiceOverlayEnabled) "✅ Voice overlay enabled" else "✅ Voice recorded"
        } else {
            buttonPlayVoice.isEnabled = false
            buttonDeleteVoice.isEnabled = false
            switchVoiceOverlay.isEnabled = false
        }
        
        // Set TTS
        isTtsOverlayEnabled = alarm.hasTtsOverlay
        switchTtsOverlay.isChecked = isTtsOverlayEnabled
        updateTtsAvailability()
        
        // Set volume settings
        ringtoneVolume = alarm.ringtoneVolume
        voiceVolume = alarm.voiceVolume
        ttsVolume = alarm.ttsVolume
        seekBarRingtoneVolume.progress = (ringtoneVolume * 100).toInt()
        seekBarVoiceVolume.progress = (voiceVolume * 100).toInt()
        textRingtoneVolume.text = "${(ringtoneVolume * 100).toInt()}%"
        textVoiceVolume.text = "${(voiceVolume * 100).toInt()}%"
        seekBarTtsVolume.progress = (ttsVolume * 100).toInt()
        textTtsVolume.text = "${(ttsVolume * 100).toInt()}%"
        updateVolumeControlsState()
    }

    private fun saveAlarm() {
        // Get the current time to ensure we're working with the correct date
        val now = Calendar.getInstance()

        val calendar = Calendar.getInstance().apply {
            // Set the date to today first
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))

            // Set the time from TimePicker (this handles 24-hour format correctly)
            set(Calendar.HOUR_OF_DAY, timePicker.hour)
            set(Calendar.MINUTE, timePicker.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the selected time has already passed today, schedule for tomorrow
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val title = editTextTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: "Alarm"
        val note = editTextNote.text?.toString() ?: ""

        val repeatDays = dayChips.filter { it.value.isChecked }.keys.toSet()

        val snoozeMinutes = when (spinnerSnooze.selectedItemPosition) {
            0 -> 5
            1 -> 10
            2 -> 15
            3 -> 20
            4 -> 25
            5 -> 30
            else -> 10
        }

        // Format time string for display using 24-hour format
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val timeString = timeFormat.format(calendar.time)

        // Log the scheduled time for debugging
        Log.d("AlarmSetupActivity", "Scheduling alarm for: $timeString (${calendar.timeInMillis})")
        Log.d("AlarmSetupActivity", "TimePicker values - Hour: ${timePicker.hour}, Minute: ${timePicker.minute}")

        val alarmItem = AlarmItem(
            id = existingAlarm?.id ?: System.currentTimeMillis().toInt(),
            time = timeString,
            isEnabled = true,
            calendar = calendar,
            repeatDays = repeatDays,
            title = title,
            note = note,
            ringtoneUri = selectedRingtoneUri,
            ringtoneName = selectedRingtoneName,
            snoozeMinutes = snoozeMinutes,
            volume = ringtoneVolume, // Use user-selected ringtone volume
            voiceRecordingPath = currentVoiceRecordingPath,
            hasVoiceOverlay = isVoiceOverlayEnabled,
            ringtoneVolume = ringtoneVolume, // User-selected ringtone volume
            voiceVolume = voiceVolume, // User-selected voice volume
            hasTtsOverlay = isTtsOverlayEnabled, // User-selected TTS overlay
            ttsVolume = ttsVolume, // User-selected TTS volume
            hasVibration = hasVibration // User-selected vibration setting
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_ALARM_ITEM, alarmItem)
        }
        setResult(RESULT_ALARM_SAVED, resultIntent)
        finish()
    }

    private fun deleteAlarm() {
        AlertDialog.Builder(this)
            .setTitle("Delete Alarm")
            .setMessage("Are you sure you want to delete this alarm?")
            .setPositiveButton("Delete") { _, _ ->
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_ALARM_ITEM, existingAlarm)
                }
                setResult(RESULT_ALARM_DELETED, resultIntent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! Tap Record to start recording.", Toast.LENGTH_SHORT).show()
                textVoiceStatus.text = "Ready to record"
            } else {
                Toast.makeText(this, " Microphone permission denied", Toast.LENGTH_SHORT).show()
                textVoiceStatus.text = "Permission denied"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun showVoiceHistoryDialog() {
        try {
            // Get the app's private directory where voice recordings are stored
            val voiceDir = File(filesDir, "voice_recordings")
            if (!voiceDir.exists()) {
                Toast.makeText(this, "📂 No recorded voices found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Get all .3gp files in the voice recordings directory
            val voiceFiles = voiceDir.listFiles { file ->
                file.isFile && file.extension.equals("3gp", ignoreCase = true)
            }
            
            if (voiceFiles.isNullOrEmpty()) {
                Toast.makeText(this, "📂 No recorded voices found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Sort files by last modified (newest first)
            val sortedFiles = voiceFiles.sortedByDescending { it.lastModified() }
            
            // Create display names for the files
            val fileNames = sortedFiles.map { file ->
                val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(file.lastModified()))
                "🎙️ Voice from $timestamp"
            }.toTypedArray()
            
            // Show selection dialog
            AlertDialog.Builder(this)
                .setTitle("📂 Select Recorded Voice")
                .setItems(fileNames) { _, which ->
                    val selectedFile = sortedFiles[which]
                    selectVoiceFile(selectedFile)
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error showing voice history dialog", e)
            Toast.makeText(this, "❌ Error loading voice history", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun selectVoiceFile(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(this, "❌ Voice file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Set the selected voice file
            currentVoiceRecordingPath = file.absolutePath
            
            // Update UI
            buttonPlayVoice.isEnabled = true
            buttonDeleteVoice.isEnabled = true
            switchVoiceOverlay.isEnabled = true
            textVoiceStatus.text = "✅ Voice selected from history"
            
            // Update volume controls
            updateVolumeControlsState()
            
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(file.lastModified()))
            Toast.makeText(this, "📂 Selected voice from $timestamp", Toast.LENGTH_SHORT).show()
            
            Log.d("AlarmSetupActivity", "Voice file selected from history: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error selecting voice file", e)
            Toast.makeText(this, "❌ Error selecting voice file", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Sound Preview Functions
    private fun playRingtonePreview() {
        try {
            // Stop any existing preview
            stopRingtonePreview()
            
            // Create and configure MediaPlayer for ringtone preview
            ringtonePreviewPlayer = MediaPlayer().apply {
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
                
                setDataSource(this@AlarmSetupActivity, selectedRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                setVolume(ringtoneVolume, ringtoneVolume)
                
                setOnPreparedListener {
                    start()
                    // Stop after 2 seconds preview
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopRingtonePreview()
                    }, 2000)
                }
                
                setOnErrorListener { _, _, _ ->
                    Log.e("AlarmSetupActivity", "Error playing ringtone preview")
                    false
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error starting ringtone preview", e)
        }
    }
    
    private fun stopRingtonePreview() {
        try {
            ringtonePreviewPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            ringtonePreviewPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error stopping ringtone preview", e)
        }
    }
    
    private fun playVoicePreview() {
        try {
            // Only play voice preview if we have a recording
            if (currentVoiceRecordingPath.isNullOrEmpty()) {
                return
            }
            
            // Stop any existing preview
            stopVoicePreview()
            
            // Create and configure MediaPlayer for voice preview
            voicePreviewPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                } else {
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                setDataSource(currentVoiceRecordingPath!!)
                setVolume(voiceVolume, voiceVolume)
                
                setOnPreparedListener {
                    start()
                    // Stop after 3 seconds preview
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopVoicePreview()
                    }, 3000)
                }
                
                setOnErrorListener { _, _, _ ->
                    Log.e("AlarmSetupActivity", "Error playing voice preview")
                    false
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error starting voice preview", e)
        }
    }
    
    private fun stopVoicePreview() {
        try {
            voicePreviewPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voicePreviewPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error stopping voice preview", e)
        }
    }
    
    private fun stopAllPreviews() {
        stopRingtonePreview()
        stopVoicePreview()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up sound preview players
        stopAllPreviews()
        
        // Clean up TTS
        tts?.shutdown()
    }
}
