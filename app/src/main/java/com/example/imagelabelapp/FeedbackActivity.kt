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
import android.view.View


private const val PREFS_NAME = "IMAGELABEL_FEEDBACK_PREFS"
private const val FEEDBACK_LIST_KEY = "FEEDBACK_LIST_DATA"
private const val TAG = "FeedbackActivity"



class FeedbackActivity : AppCompatActivity() {

    private lateinit var feedbackDisplay: TextView
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var dashboardTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //  Use the XML layout instead of programmatic views
        setContentView(R.layout.feedback_activity)

        setupUI()
        loadAndDisplayFeedback()
    }

    private fun setupUI() {
        dashboardTitle = findViewById(R.id.dashboardTitle)
        recyclerView = findViewById(R.id.feedbackRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        // Set up RecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
    }


    private fun loadAndDisplayFeedback() {
        try {
            val sharedPrefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            Log.d(TAG, "SharedPreferences initialized")

            val json = sharedPrefs.getString(FEEDBACK_LIST_KEY, null)
            Log.d(TAG, "Raw JSON from SharedPreferences: $json")

            val entries: List<Entry> = if (json != null) {
                try {
                    val type = object : TypeToken<List<Entry>>() {}.type
                    val result: List<Entry> = Gson().fromJson(json, type)
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

    private fun updateDisplay(entries: List<Entry>) {
        if (entries.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE

            // Sort by newest first
            val sortedEntries = entries.sortedByDescending { it.timestamp }

            // Set up the adapter
            recyclerView.adapter = FeedbackAdapter(sortedEntries)
        }

        // Update the total count in the title
        dashboardTitle.text = "📊 FEEDBACK DASHBOARD\nTotal Saved Entries: ${entries.size}"
    }



    // Helper method to add test data (for debugging)
    private fun addTestFeedback() {
        val testEntry = Entry(
            imageFilePath = "/storage/emulated/0/test_image.jpg",
            correctLabel = "Test Label",
            timestamp = System.currentTimeMillis()
        )

        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = sharedPrefs.getString(FEEDBACK_LIST_KEY, null)

        val entries: MutableList<Entry> = if (existingJson != null) {
            val type = object : TypeToken<MutableList<Entry>>() {}.type
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