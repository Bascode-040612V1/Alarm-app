package com.yourapp.test.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SNOOZE = "com.yourapp.test.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.yourapp.test.alarm.DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "onReceive called with action: ${intent.action}")
        when (intent.action) {
            ACTION_SNOOZE -> {
                Log.d("AlarmReceiver", "Processing snooze action")
                handleSnooze(context, intent)
            }
            ACTION_DISMISS -> {
                Log.d("AlarmReceiver", "Processing dismiss action")
                handleDismiss(context, intent)
            }
            else -> {
                Log.d("AlarmReceiver", "Processing regular alarm trigger")
                handleAlarm(context, intent)
            }
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: ""
        val alarmTitle = intent.getStringExtra("ALARM_TITLE") ?: "Alarm"
        val alarmNote = intent.getStringExtra("ALARM_NOTE") ?: ""
        val ringtoneUriString = intent.getStringExtra("RINGTONE_URI")
        val ringtoneName = intent.getStringExtra("RINGTONE_NAME") ?: "Default"
        val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 10)
        val isRepeating = intent.getBooleanExtra("IS_REPEATING", false)
        val repeatDay = intent.getIntExtra("REPEAT_DAY", -1)
        val voiceRecordingPath = intent.getStringExtra("VOICE_RECORDING_PATH")
        val hasVoiceOverlay = intent.getBooleanExtra("HAS_VOICE_OVERLAY", false)
        val hasTtsOverlay = intent.getBooleanExtra("HAS_TTS_OVERLAY", false)
        val ringtoneVolume = intent.getFloatExtra("RINGTONE_VOLUME", 0.8f)
        val voiceVolume = intent.getFloatExtra("VOICE_VOLUME", 1.0f)
        val ttsVolume = intent.getFloatExtra("TTS_VOLUME", 1.0f)
        val hasVibration = intent.getBooleanExtra("HAS_VIBRATION", true) // CRITICAL FIX: Get user vibration setting
        
        // Fix: Properly handle ringtone URI - don't default to alarm if custom ringtone is selected
        val ringtoneUri = if (!ringtoneUriString.isNullOrEmpty() && ringtoneUriString != "null") {
            try {
                Uri.parse(ringtoneUriString)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to parse ringtone URI: $ringtoneUriString", e)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        } else {
            // Only use default if no ringtone was specifically selected
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        
        Log.d("AlarmReceiver", "Alarm triggered - Ringtone: $ringtoneName, URI: $ringtoneUri, Voice: $hasVoiceOverlay, TTS: $hasTtsOverlay")
        
        // Wake up the device
        wakeUpDevice(context)
        
        // Launch full-screen alarm activity with voice data and volume settings
        launchAlarmScreen(context, alarmId, alarmTime, alarmTitle, alarmNote, ringtoneUri, ringtoneName, snoozeMinutes, voiceRecordingPath, hasVoiceOverlay, hasTtsOverlay, ringtoneVolume, voiceVolume, ttsVolume)
        
        // Also create notification as backup
        createNotificationChannel(context)
        showNotification(context, alarmId, alarmTitle, alarmTime, alarmNote, ringtoneUri, snoozeMinutes)
        if (hasVibration) {
            vibrateDevice(context)
        }
        
        // If it's a repeating alarm, schedule the next occurrence
        if (isRepeating && repeatDay != -1) {
            scheduleNextRepeatingAlarm(context, intent, repeatDay)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 10)
        
        // Extract all alarm data to preserve for snooze
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: ""
        val alarmTitle = intent.getStringExtra("ALARM_TITLE") ?: "Alarm"
        val alarmNote = intent.getStringExtra("ALARM_NOTE") ?: ""
        val ringtoneUriString = intent.getStringExtra("RINGTONE_URI")
        val ringtoneName = intent.getStringExtra("RINGTONE_NAME") ?: "Default"
        val isRepeating = intent.getBooleanExtra("IS_REPEATING", false)
        val repeatDay = intent.getIntExtra("REPEAT_DAY", -1)
        val voiceRecordingPath = intent.getStringExtra("VOICE_RECORDING_PATH")
        val hasVoiceOverlay = intent.getBooleanExtra("HAS_VOICE_OVERLAY", false)
        val hasTtsOverlay = intent.getBooleanExtra("HAS_TTS_OVERLAY", false)
        val ringtoneVolume = intent.getFloatExtra("RINGTONE_VOLUME", 0.8f)
        val voiceVolume = intent.getFloatExtra("VOICE_VOLUME", 1.0f)
        val ttsVolume = intent.getFloatExtra("TTS_VOLUME", 1.0f)
        val hasVibration = intent.getBooleanExtra("HAS_VIBRATION", true) // CRITICAL FIX: Get user vibration setting
        
        Log.d("AlarmReceiver", "Snoozing alarm - Preserving ringtone: $ringtoneName, URI: $ringtoneUriString, Volumes: R=$ringtoneVolume V=$voiceVolume TTS=$ttsVolume")
        
        // Dismiss current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)
        
        // Stop the AlarmScreenActivity if it's running
        val stopAlarmIntent = Intent(context, AlarmScreenActivity::class.java).apply {
            action = "STOP_ALARM"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            context.startActivity(stopAlarmIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Could not stop AlarmScreenActivity: ${e.message}")
        }
        
        // Broadcast to stop any running alarm screen
        val stopBroadcast = Intent("com.yourapp.test.alarm.STOP_ALARM").apply {
            putExtra("ALARM_ID", alarmId)
        }
        context.sendBroadcast(stopBroadcast)
        
        // Schedule snooze alarm with ALL original data preserved
        val snoozeTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, snoozeMinutes)
        }
        
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            // Preserve ALL original alarm data for the snoozed alarm
            putExtra("ALARM_ID", alarmId + 10000) // Different ID for snooze
            putExtra("ALARM_TIME", alarmTime)
            putExtra("ALARM_TITLE", alarmTitle)
            putExtra("ALARM_NOTE", alarmNote)
            putExtra("RINGTONE_URI", ringtoneUriString) // Preserve original ringtone URI
            putExtra("RINGTONE_NAME", ringtoneName) // Preserve original ringtone name
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("IS_REPEATING", isRepeating)
            putExtra("REPEAT_DAY", repeatDay)
            putExtra("VOICE_RECORDING_PATH", voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", hasTtsOverlay)
            putExtra("RINGTONE_VOLUME", ringtoneVolume)
            putExtra("VOICE_VOLUME", voiceVolume)
            putExtra("TTS_VOLUME", ttsVolume)
            putExtra("HAS_VIBRATION", hasVibration) // CRITICAL FIX: Preserve user vibration setting
        }
        
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, alarmId + 10000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime.timeInMillis,
                    snoozePendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime.timeInMillis,
                    snoozePendingIntent
                )
            }
            Log.d("AlarmReceiver", "Snooze scheduled for ${snoozeMinutes} minutes with ringtone: $ringtoneName")
        } catch (e: SecurityException) {
            Log.e("AlarmReceiver", "Failed to schedule snooze: ${e.message}")
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        
        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)
    }

    private fun scheduleNextRepeatingAlarm(context: Context, intent: Intent, dayOfWeek: Int) {
        val nextWeek = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, intent.getStringExtra("ALARM_TIME")?.split(":")?.get(0)?.toInt() ?: 0)
            set(Calendar.MINUTE, intent.getStringExtra("ALARM_TIME")?.split(":")?.get(1)?.toInt() ?: 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtras(intent.extras ?: Bundle())
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            intent.getIntExtra("ALARM_ID", 0),
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            nextWeek.timeInMillis,
            AlarmManager.INTERVAL_DAY * 7,
            pendingIntent
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Notifications"
            val descriptionText = "Notifications for alarm clock"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(null, null) // Explicitly disable sound for notification channel
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        context: Context, 
        alarmId: Int, 
        title: String, 
        time: String, 
        note: String, 
        ringtoneUri: Uri?, 
        snoozeMinutes: Int,
        intent: Intent? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action - preserve ALL original alarm data
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_TIME", time)
            putExtra("ALARM_NOTE", note)
            putExtra("RINGTONE_URI", ringtoneUri?.toString())
            
            // Preserve all additional data from original intent if available
            intent?.let { originalIntent ->
                putExtra("RINGTONE_NAME", originalIntent.getStringExtra("RINGTONE_NAME") ?: "Default")
                putExtra("IS_REPEATING", originalIntent.getBooleanExtra("IS_REPEATING", false))
                putExtra("REPEAT_DAY", originalIntent.getIntExtra("REPEAT_DAY", -1))
                putExtra("VOICE_RECORDING_PATH", originalIntent.getStringExtra("VOICE_RECORDING_PATH"))
                putExtra("HAS_VOICE_OVERLAY", originalIntent.getBooleanExtra("HAS_VOICE_OVERLAY", false))
                putExtra("HAS_TTS_OVERLAY", originalIntent.getBooleanExtra("HAS_TTS_OVERLAY", false))
                putExtra("RINGTONE_VOLUME", originalIntent.getFloatExtra("RINGTONE_VOLUME", 0.8f))
                putExtra("VOICE_VOLUME", originalIntent.getFloatExtra("VOICE_VOLUME", 1.0f))
                putExtra("TTS_VOLUME", originalIntent.getFloatExtra("TTS_VOLUME", 1.0f))
            }
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, alarmId + 1000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, alarmId + 2000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (note.isNotEmpty()) "$time - $note" else time
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(null) // Explicitly set no sound for notification
            .setVibrate(longArrayOf(0, 1000, 1000, 1000))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_snooze, "Snooze ($snoozeMinutes min)", snoozePendingIntent)
            .addAction(R.drawable.ic_dismiss, "Dismiss", dismissPendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alarmId, notification)
    }

    private fun launchAlarmScreen(
        context: Context,
        alarmId: Int,
        alarmTime: String,
        alarmTitle: String,
        alarmNote: String,
        ringtoneUri: Uri?,
        ringtoneName: String,
        snoozeMinutes: Int,
        voiceRecordingPath: String?,
        hasVoiceOverlay: Boolean,
        hasTtsOverlay: Boolean,
        ringtoneVolume: Float,
        voiceVolume: Float,
        ttsVolume: Float
    ) {
        val alarmScreenIntent = Intent(context, AlarmScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(AlarmScreenActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_TIME, alarmTime)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_NOTE, alarmNote)
            putExtra(AlarmScreenActivity.EXTRA_RINGTONE_URI, ringtoneUri.toString())
            putExtra(AlarmScreenActivity.EXTRA_RINGTONE_NAME, ringtoneName)
            putExtra(AlarmScreenActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(AlarmScreenActivity.EXTRA_VOICE_RECORDING_PATH, voiceRecordingPath)
            putExtra(AlarmScreenActivity.EXTRA_HAS_VOICE_OVERLAY, hasVoiceOverlay)
            putExtra(AlarmScreenActivity.EXTRA_HAS_TTS_OVERLAY, hasTtsOverlay)
            putExtra(AlarmScreenActivity.EXTRA_RINGTONE_VOLUME, ringtoneVolume)
            putExtra(AlarmScreenActivity.EXTRA_VOICE_VOLUME, voiceVolume)
            putExtra(AlarmScreenActivity.EXTRA_TTS_VOLUME, ttsVolume)
        }
        
        try {
            context.startActivity(alarmScreenIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            // If we can't start the activity, fall back to notification only
        }
    }

    private fun vibrateDevice(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1)
        }
    }

    private fun wakeUpDevice(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        // Strategy 1: Create multiple wake locks for maximum reliability
        val fullWakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
            PowerManager.ON_AFTER_RELEASE,
            "AlarmApp:FullWakeLock"
        )
        
        val screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmApp:ScreenWakeLock"
        )
        
        // Strategy 2: Acquire both wake locks for maximum wake power
        fullWakeLock.acquire(120000) // 2 minutes
        screenWakeLock.acquire(120000) // 2 minutes
        
        // Strategy 3: Force screen on for different Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // For newer Android versions
            if (!powerManager.isInteractive) {
                Log.d("AlarmReceiver", "Screen is OFF - Forcing wake up")
                
                // Additional partial wake lock
                val partialWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AlarmApp:PartialWakeLock"
                )
                partialWakeLock.acquire(60000)
            }
        } else {
            // For older Android versions
            @Suppress("DEPRECATION")
            val legacyWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AlarmApp:LegacyWakeLock"
            )
            legacyWakeLock.acquire(60000)
        }
        
        // Strategy 4: Handle different power states
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (powerManager.isDeviceIdleMode) {
                    Log.d("AlarmReceiver", "Device in Doze mode - Wake locks should override")
                }
                
                if (!powerManager.isInteractive) {
                    Log.d("AlarmReceiver", "Screen OFF detected - Multiple wake strategies activated")
                }
            }
            
            // Check keyguard state
            if (keyguardManager.isKeyguardLocked) {
                Log.d("AlarmReceiver", "Device locked - Alarm will show over lock screen")
            }
            
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Wake strategy error: ${e.message}")
        }
        
        Log.d("AlarmReceiver", "Enhanced wake sequence completed - Should wake from screen OFF")
    }
}
