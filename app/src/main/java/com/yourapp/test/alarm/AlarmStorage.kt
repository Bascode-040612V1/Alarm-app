package com.yourapp.test.alarm

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class AlarmStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("alarm_storage", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_ALARMS = "saved_alarms"
    }
    
    data class SerializableAlarm(
        val id: Int,
        val time: String,
        val isEnabled: Boolean,
        val hour: Int,
        val minute: Int,
        val repeatDays: Set<Int>,
        val title: String,
        val note: String,
        val ringtoneUriString: String?,
        val ringtoneName: String,
        val snoozeMinutes: Int
    )
    
    fun saveAlarms(alarms: List<AlarmItem>) {
        val serializableAlarms = alarms.map { alarm ->
            SerializableAlarm(
                id = alarm.id,
                time = alarm.time,
                isEnabled = alarm.isEnabled,
                hour = alarm.calendar.get(Calendar.HOUR_OF_DAY),
                minute = alarm.calendar.get(Calendar.MINUTE),
                repeatDays = alarm.repeatDays,
                title = alarm.title,
                note = alarm.note,
                ringtoneUriString = alarm.ringtoneUri?.toString(),
                ringtoneName = alarm.ringtoneName,
                snoozeMinutes = alarm.snoozeMinutes
            )
        }
        
        val json = gson.toJson(serializableAlarms)
        sharedPreferences.edit()
            .putString(KEY_ALARMS, json)
            .apply()
    }
    
    fun loadAlarms(): List<AlarmItem> {
        val json = sharedPreferences.getString(KEY_ALARMS, null) ?: return emptyList()
        
        try {
            val type = object : TypeToken<List<SerializableAlarm>>() {}.type
            val serializableAlarms: List<SerializableAlarm> = gson.fromJson(json, type)
            
            return serializableAlarms.map { serializable ->
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, serializable.hour)
                    set(Calendar.MINUTE, serializable.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    
                    // If not repeating and time has passed today, set for tomorrow
                    if (serializable.repeatDays.isEmpty() && before(Calendar.getInstance())) {
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                }
                
                AlarmItem(
                    id = serializable.id,
                    time = serializable.time,
                    isEnabled = serializable.isEnabled,
                    calendar = calendar,
                    repeatDays = serializable.repeatDays,
                    title = serializable.title,
                    note = serializable.note,
                    ringtoneUri = serializable.ringtoneUriString?.let { Uri.parse(it) },
                    ringtoneName = serializable.ringtoneName,
                    snoozeMinutes = serializable.snoozeMinutes
                )
            }
        } catch (e: Exception) {
            // If there's an error loading, return empty list
            return emptyList()
        }
    }
    
    fun clearAlarms() {
        sharedPreferences.edit()
            .remove(KEY_ALARMS)
            .apply()
    }
}
