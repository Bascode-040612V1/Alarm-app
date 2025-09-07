package com.yourapp.test.alarm

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.util.*

data class AlarmItem(
    val id: Int,
    val time: String,
    var isEnabled: Boolean,
    val calendar: Calendar,
    val repeatDays: Set<Int> = emptySet(), // Calendar.SUNDAY, MONDAY, etc.
    val title: String = "Alarm",
    val note: String = "",
    val ringtoneUri: Uri? = null,
    val ringtoneName: String = "Default",
    val snoozeMinutes: Int = 10,
    val voiceRecordingPath: String? = null, // Path to recorded voice file
    val hasVoiceOverlay: Boolean = false, // Whether to overlay voice on ringtone
    val ringtoneVolume: Float = 0.8f, // Ringtone volume from 0.0 to 1.0, default 80%
    val voiceVolume: Float = 1.0f, // Voice overlay volume from 0.0 to 1.0, default 100%
    val hasTtsOverlay: Boolean = false, // Whether to use TTS overlay for reading note
    val ttsVolume: Float = 1.0f, // TTS volume from 0.0 to 1.0, default 100%
    val hasVibration: Boolean = true // Whether to vibrate when alarm triggers, default enabled
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readInt() == 1,
        Calendar.getInstance().apply { timeInMillis = parcel.readLong() },
        parcel.createIntArray()?.toSet() ?: emptySet(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readParcelable(Uri::class.java.classLoader),
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt() == 1,
        parcel.readFloat(), // ringtoneVolume
        parcel.readFloat(),  // voiceVolume
        parcel.readInt() == 1, // hasTtsOverlay
        parcel.readFloat(),  // ttsVolume
        parcel.readInt() == 1 // hasVibration
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(time)
        parcel.writeInt(if (isEnabled) 1 else 0)
        parcel.writeLong(calendar.timeInMillis)
        parcel.writeIntArray(repeatDays.toIntArray())
        parcel.writeString(title)
        parcel.writeString(note)
        parcel.writeParcelable(ringtoneUri, flags)
        parcel.writeString(ringtoneName)
        parcel.writeInt(snoozeMinutes)
        parcel.writeString(voiceRecordingPath)
        parcel.writeInt(if (hasVoiceOverlay) 1 else 0)
        parcel.writeFloat(ringtoneVolume) // Add ringtone volume
        parcel.writeFloat(voiceVolume)    // Add voice volume
        parcel.writeInt(if (hasTtsOverlay) 1 else 0) // Add hasTtsOverlay
        parcel.writeFloat(ttsVolume)      // Add ttsVolume
        parcel.writeInt(if (hasVibration) 1 else 0) // Add hasVibration
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AlarmItem> {
        override fun createFromParcel(parcel: Parcel): AlarmItem {
            return AlarmItem(parcel)
        }

        override fun newArray(size: Int): Array<AlarmItem?> {
            return arrayOfNulls(size)
        }
    }
    
    fun isRepeating(): Boolean = repeatDays.isNotEmpty()
    
    fun getRepeatDaysString(): String {
        if (repeatDays.isEmpty()) return "Once"
        
        val dayNames = mapOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
        
        return when {
            repeatDays.size == 7 -> "Every day"
            repeatDays.containsAll(setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)) && repeatDays.size == 5 -> "Weekdays"
            repeatDays.containsAll(setOf(Calendar.SATURDAY, Calendar.SUNDAY)) && repeatDays.size == 2 -> "Weekends"
            else -> repeatDays.sortedBy { 
                when(it) {
                    Calendar.SUNDAY -> 7
                    else -> it
                }
            }.joinToString(", ") { dayNames[it] ?: "" }
        }
    }
}
