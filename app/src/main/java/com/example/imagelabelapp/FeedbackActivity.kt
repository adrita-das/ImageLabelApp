package com.example.imagelabelapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

private const val PREFS_NAME = "ImageLabelFeedbackPrefs"
private const val FEEDBACK_LIST_KEY = "FEEDBACK_LIST"
private const val TAG = "FeedbackActivity"

data class FeedbackEntry(
    val imageFilePath: String,
    val correctLabel: String,
    val timestamp: Long
)

class FeedbackActivity : AppCompatActivity() {

    private lateinit var feedbackDisplay: TextView
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "FeedbackActivity onCreate started")

        setupUI()
        loadAndDisplayFeedback()
    }

    private fun setupUI() {
        // Create ScrollView to handle long lists
        val scrollView = ScrollView(this)

        // Create TextView programmatically
        feedbackDisplay = TextView(this).apply {
            textSize = 14f
            setPadding(32, 32, 32, 32)
            text = "Loading feedback..."
        }

        scrollView.addView(feedbackDisplay)
        setContentView(scrollView)

        Log.d(TAG, "UI setup completed")
    }

    private fun loadAndDisplayFeedback() {
        try {
            val sharedPrefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            Log.d(TAG, "SharedPreferences initialized")

            val json = sharedPrefs.getString(FEEDBACK_LIST_KEY, null)
            Log.d(TAG, "Raw JSON from SharedPreferences: $json")

            val entries: List<FeedbackEntry> = if (json != null) {
                try {
                    val type = object : TypeToken<List<FeedbackEntry>>() {}.type
                    val result: List<FeedbackEntry> = Gson().fromJson(json, type)
                    Log.d(TAG, "Successfully parsed ${result.size} entries from JSON")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing JSON: ${e.message}")
                    emptyList()
                }
            } else {
                Log.d(TAG, "No JSON data found in SharedPreferences")
                emptyList()
            }

            updateDisplay(entries)

        } catch (e: Exception) {
            Log.e(TAG, "Error in loadAndDisplayFeedback: ${e.message}")
            feedbackDisplay.text = "Error loading feedback: ${e.message}"
        }
    }

    private fun updateDisplay(entries: List<FeedbackEntry>) {
        Log.d(TAG, "Updating display with ${entries.size} entries")

        val sb = StringBuilder()
        sb.append("📊 FEEDBACK DASHBOARD\n")
        sb.append("Total Saved Entries: ${entries.size}\n\n")
        sb.append("=========================================\n\n")

        if (entries.isEmpty()) {
            sb.append("📝 No feedback entries found.\n\n")
            sb.append("To add feedback:\n")
            sb.append("1. Use the image labeling feature\n")
            sb.append("2. When ML Kit gives wrong results\n")
            sb.append("3. Provide correct label feedback\n\n")
            sb.append("Your feedback will appear here!")
        } else {
            // Display entries in reverse chronological order (newest first)
            entries.sortedByDescending { it.timestamp }.forEachIndexed { index, entry ->
                val time = dateFormat.format(Date(entry.timestamp))
                val fileName = entry.imageFilePath.substringAfterLast("/")

                sb.append("📋 Entry #${index + 1}\n")
                sb.append("🏷️ Correct Label: ${entry.correctLabel}\n")
                sb.append("📅 Time: $time\n")
                sb.append("📸 Image: $fileName\n")
                sb.append("📁 Path: ${entry.imageFilePath}\n")
                sb.append("-----------------------------------------\n\n")
            }
        }

        // Display in UI
        feedbackDisplay.text = sb.toString()

        Log.d(TAG, "Display updated successfully")
    }

    // Helper method to add test data (for debugging)
    private fun addTestFeedback() {
        val testEntry = FeedbackEntry(
            imageFilePath = "/storage/emulated/0/test_image.jpg",
            correctLabel = "Test Label",
            timestamp = System.currentTimeMillis()
        )

        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = sharedPrefs.getString(FEEDBACK_LIST_KEY, null)

        val entries: MutableList<FeedbackEntry> = if (existingJson != null) {
            val type = object : TypeToken<MutableList<FeedbackEntry>>() {}.type
            Gson().fromJson(existingJson, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }

        entries.add(testEntry)

        val newJson = Gson().toJson(entries)
        sharedPrefs.edit().putString(FEEDBACK_LIST_KEY, newJson).apply()

        Log.d(TAG, "Test feedback added")
    }
}