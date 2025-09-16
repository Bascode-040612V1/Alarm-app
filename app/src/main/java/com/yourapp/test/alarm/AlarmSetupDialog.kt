package com.yourapp.test.alarm

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class AlarmSetupDialog(
    context: Context,
    private val existingAlarm: AlarmItem? = null,
    private val onAlarmSaved: (AlarmItem) -> Unit
) : Dialog(context) {

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
    
    private var selectedRingtoneUri: Uri? = null
    private var selectedRingtoneName: String = "Default"
    
    private val dayChips by lazy {
        mapOf(
            Calendar.SUNDAY to chipSunday,
            Calendar.MONDAY to chipMonday,
            Calendar.TUESDAY to chipTuesday,
            Calendar.WEDNESDAY to chipWednesday,
            Calendar.THURSDAY to chipThursday,
            Calendar.FRIDAY to chipFriday,
            Calendar.SATURDAY to chipSaturday
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_alarm_setup)
        
        initViews()
        setupTimePicker()
        setupSnoozeSpinner()
        setupRingtoneSelector()
        setupButtons()
        
        existingAlarm?.let { populateExistingAlarm(it) }
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
    }
    
    private fun setupTimePicker() {
        // Set TimePicker to use system's 12/24-hour format
        timePicker.setIs24HourView(DateFormat.is24HourFormat(context))
    }

    private fun setupSnoozeSpinner() {
        val snoozeOptions = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "25 minutes", "30 minutes")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, snoozeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSnooze.adapter = adapter
        spinnerSnooze.setSelection(1) // Default to 10 minutes
    }

    private fun setupRingtoneSelector() {
        layoutRingtoneSelector.setOnClickListener {
            // Note: In a real implementation, you'd need to handle this with an activity result
            // For now, we'll use a simple dialog to simulate ringtone selection
            showRingtoneSelectionDialog()
        }
    }

    private fun showRingtoneSelectionDialog() {
        val ringtoneManager = RingtoneManager(context)
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
        
        val cursor = ringtoneManager.cursor
        val ringtoneNames = mutableListOf<String>()
        val ringtoneUris = mutableListOf<Uri?>()
        
        // Add default option
        ringtoneNames.add("Default")
        ringtoneUris.add(null)
        
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
        
        AlertDialog.Builder(context)
            .setTitle("Select Ringtone")
            .setItems(ringtoneNames.toTypedArray()) { _, which ->
                selectedRingtoneName = ringtoneNames[which]
                selectedRingtoneUri = ringtoneUris[which]
                textSelectedRingtone.text = selectedRingtoneName
                
                // Preview the selected ringtone
                ringtoneUris[which]?.let { uri ->
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    ringtone?.play()
                    // Stop after 2 seconds
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        ringtone?.stop()
                    }, 2000)
                }
            }
            .show()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.buttonCancel).setOnClickListener {
            dismiss()
        }
        
        findViewById<Button>(R.id.buttonSave).setOnClickListener {
            saveAlarm()
        }
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
        alarm.repeatDays.forEach { day ->
            dayChips[day]?.isChecked = true
        }
        
        // Set ringtone
        selectedRingtoneUri = alarm.ringtoneUri
        selectedRingtoneName = alarm.ringtoneName
        textSelectedRingtone.text = selectedRingtoneName
        
        // Set snooze
        val snoozeIndex = when (alarm.snoozeMinutes) {
            5 -> 0
            10 -> 1
            15 -> 2
            20 -> 3
            25 -> 4
            30 -> 5
            else -> 1
        }
        spinnerSnooze.setSelection(snoozeIndex)
    }

    private fun saveAlarm() {
        val hour = timePicker.hour
        val minute = timePicker.minute
        val title = editTextTitle.text?.toString()?.trim() ?: "Alarm"
        val note = editTextNote.text?.toString()?.trim() ?: ""
        
        // Get selected repeat days
        val repeatDays = mutableSetOf<Int>()
        dayChips.forEach { (day, chip) ->
            if (chip.isChecked) {
                repeatDays.add(day)
            }
        }
        
        // Get snooze minutes
        val snoozeMinutes = when (spinnerSnooze.selectedItemPosition) {
            0 -> 5
            1 -> 10
            2 -> 15
            3 -> 20
            4 -> 25
            5 -> 30
            else -> 10
        }
        
        // Create alarm time
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If not repeating and time has passed today, set for tomorrow
            if (repeatDays.isEmpty() && before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val alarmId = existingAlarm?.id ?: System.currentTimeMillis().toInt()
        val alarmItem = AlarmItem(
            id = alarmId,
            time = String.format("%02d:%02d", hour, minute),
            isEnabled = existingAlarm?.isEnabled ?: true,
            calendar = alarmTime,
            repeatDays = repeatDays,
            title = title,
            note = note,
            ringtoneUri = selectedRingtoneUri,
            ringtoneName = selectedRingtoneName,
            snoozeMinutes = snoozeMinutes
        )
        
        onAlarmSaved(alarmItem)
        dismiss()
    }
}
