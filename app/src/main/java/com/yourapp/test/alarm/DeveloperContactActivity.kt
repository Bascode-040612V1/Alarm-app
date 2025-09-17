package com.yourapp.test.alarm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourapp.test.alarm.databinding.ActivityDeveloperContactBinding

class DeveloperContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityDeveloperContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupContactOptions()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Contact Developers"
        }
    }
    
    private fun setupContactOptions() {
        // Email contact
        binding.cardEmail.setOnClickListener {
            sendEmail()
        }
        
        // GitHub repository
        binding.cardGithub.setOnClickListener {
            openGitHub()
        }
        
        // App feedback
        binding.cardFeedback.setOnClickListener {
            sendFeedback()
        }
        
        // Bug report
        binding.cardBugReport.setOnClickListener {
            reportBug()
        }
    }
    
    private fun sendEmail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:developer@alarmapp.com")
                putExtra(Intent.EXTRA_SUBJECT, "Alarm App - Contact")
                putExtra(Intent.EXTRA_TEXT, "Hi developers,\n\nI'm reaching out regarding the Alarm App.\n\n")
            }
            
            if (emailIntent.resolveActivity(packageManager) != null) {
                startActivity(emailIntent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to send email", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGitHub() {
        try {
            val githubIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://github.com/yourapp/alarm-app")
            }
            
            if (githubIntent.resolveActivity(packageManager) != null) {
                startActivity(githubIntent)
            } else {
                Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open GitHub", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendFeedback() {
        try {
            val feedbackIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:feedback@alarmapp.com")
                putExtra(Intent.EXTRA_SUBJECT, "Alarm App - Feedback")
                putExtra(Intent.EXTRA_TEXT, "Hi team,\n\nI'd like to share feedback about the Alarm App:\n\n" +
                        "What I like:\n\n" +
                        "What could be improved:\n\n" +
                        "Additional features I'd like to see:\n\n" +
                        "Thanks for creating this app!")
            }
            
            if (feedbackIntent.resolveActivity(packageManager) != null) {
                startActivity(feedbackIntent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to send feedback", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun reportBug() {
        try {
            val bugReportIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:bugs@alarmapp.com")
                putExtra(Intent.EXTRA_SUBJECT, "Alarm App - Bug Report")
                putExtra(Intent.EXTRA_TEXT, "Hi developers,\n\nI found a bug in the Alarm App:\n\n" +
                        "Steps to reproduce:\n1. \n2. \n3. \n\n" +
                        "Expected behavior:\n\n" +
                        "Actual behavior:\n\n" +
                        "Device information:\n" +
                        "- Android version: ${android.os.Build.VERSION.RELEASE}\n" +
                        "- Device model: ${android.os.Build.MODEL}\n" +
                        "- App version: 1.0\n\n" +
                        "Additional notes:\n\n")
            }
            
            if (bugReportIntent.resolveActivity(packageManager) != null) {
                startActivity(bugReportIntent)
            } else {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to send bug report", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}