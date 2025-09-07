package com.yourapp.test.alarm

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private val alarmList: List<AlarmItem>,
    private val onDeleteClick: (AlarmItem) -> Unit,
    private val onToggleClick: (AlarmItem, Boolean) -> Unit,
    private val onEditClick: (AlarmItem) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.textAlarmTime)
        val titleText: TextView = itemView.findViewById(R.id.textAlarmTitle)
        val repeatText: TextView = itemView.findViewById(R.id.textAlarmRepeat)
        val ringtoneText: TextView = itemView.findViewById(R.id.textAlarmRingtone)
        val enableSwitch: Switch = itemView.findViewById(R.id.switchAlarmEnabled)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteAlarm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]
        
        // Format time according to system preference
        val displayTime = formatTimeForDisplay(holder.itemView.context, alarm.time)
        holder.timeText.text = displayTime
        
        holder.titleText.text = if (alarm.title.isNotEmpty()) alarm.title else "Alarm"
        holder.repeatText.text = alarm.getRepeatDaysString()
        holder.ringtoneText.text = alarm.ringtoneName
        holder.enableSwitch.isChecked = alarm.isEnabled
        
        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleClick(alarm, isChecked)
        }
        
        holder.deleteButton.setOnClickListener {
            onDeleteClick(alarm)
        }
        
        holder.itemView.setOnClickListener {
            onEditClick(alarm)
        }
    }

    override fun getItemCount(): Int = alarmList.size
    
    private fun formatTimeForDisplay(context: android.content.Context, time24Hour: String): String {
        val is24HourFormat = DateFormat.is24HourFormat(context)
        if (is24HourFormat) {
            return time24Hour
        }
        
        // Convert 24-hour to 12-hour format
        val parts = time24Hour.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1]
        
        return when {
            hour == 0 -> "12:$minute AM"
            hour < 12 -> "$hour:$minute AM"
            hour == 12 -> "12:$minute PM"
            else -> "${hour - 12}:$minute PM"
        }
    }
}
