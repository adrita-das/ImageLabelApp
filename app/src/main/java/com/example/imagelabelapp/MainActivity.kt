package com.example.imagelabelapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.jvm.java
class MainActivity : AppCompatActivity() {

    // SharedPreferences key for the list of feedback entries
    private val PREFS_NAME = "ImageLabelAppPrefs"
    private val KEY_FEEDBACK_LIST = "feedbackList"

    // UI Components
    private lateinit var objectImage: ImageView
    private lateinit var labelText: TextView
    private lateinit var captureImgBtn: Button
    private lateinit var galleryBtn: Button
    private lateinit var feedbackText: EditText
    private lateinit var feedbackButton: Button
    private lateinit var viewButton: Button // New button reference for history

    // ML Kit
    private lateinit var imageLabeler: ImageLabeler

    // Launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    // State
    private var currentImageUri: Uri? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Initialize UI
        objectImage = findViewById(R.id.objectImage)
        labelText = findViewById(R.id.labelText)
        captureImgBtn = findViewById(R.id.captureImgBtn)
        galleryBtn = findViewById(R.id.galleryBtn)
        feedbackText = findViewById(R.id.feedbackText)
        feedbackButton = findViewById(R.id.feedbackButton)
        viewButton = findViewById(R.id.viewHistoryBtn) // Initialize the new button here

        // 2. Setup
        checkCameraPermission()
        imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        // 3. Register Activity Launchers (omitted for brevity, assume correct)
        // ... (existing cameraLauncher and galleryLauncher registration logic remains here)
        // Handles image captured from Camera (returns Bitmap, needs conversion to Uri)
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val extras = result.data?.extras
                val imageBitmap = extras?.getParcelable("data", Bitmap::class.java)
                if (imageBitmap != null) {
                    val imageUri = saveBitmapToCache(imageBitmap) // Helper function to get Uri
                    objectImage.setImageBitmap(imageBitmap)
                    labelImage(imageBitmap, imageUri) // Pass both Bitmap and Uri
                } else {
                    labelText.text = "Unable to capture Image"
                }
            }
        }

        // Handles image selected from Gallery (returns Uri)
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    objectImage.setImageBitmap(bitmap)
                    labelImage(bitmap, imageUri) // Pass both Bitmap and Uri
                }
            }
        }

        // 4. Set Click Listeners
        captureImgBtn.setOnClickListener {
            val clickPicture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (clickPicture.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(clickPicture)
            }
        }

        galleryBtn.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(galleryIntent)
        }

        // Feedback Button Listener - Calls the new SharedPreferences saving function
        feedbackButton.setOnClickListener {
            val correctLabel = feedbackText.text.toString().trim()
            if (currentImageUri != null && correctLabel.isNotEmpty()) {
                 saveFeedbackToPrefs(currentImageUri!!,correctLabel)
                feedbackText.text.clear()
            } else {
                Toast.makeText(this, "Please capture an image and enter a label.", Toast.LENGTH_SHORT).show()
            }
        }

        // Listener for the new View History button (outside the dialog)
        viewButton.setOnClickListener {
            navigateToFeedbackActivity()
        }
    }

    // --- NEW: Function to show the confirmation dialog ---
    private fun showFeedbackSubmittedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Feedback Saved!")
            .setMessage("Thanks for your feedback! It has been successfully saved.")
            .setPositiveButton("View History") { dialog, _ ->
                navigateToFeedbackActivity()
                dialog.dismiss()
            }
            .setNegativeButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // --- NEW: Function to navigate to the history screen ---
    private fun navigateToFeedbackActivity() {
        // We use FeedbackActivity since the XML was named feedback_activity.xml
        val intent = Intent(this, FeedbackEntry::class.java)
        startActivity(intent)
    }

    // --- ML KIT LOGIC (omitted for brevity, assume correct) ---
    // ... (labelImage and displayLabel functions remain here)

    private fun labelImage(bitmap: Bitmap , imageUri: Uri?) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        imageLabeler.process(inputImage).addOnSuccessListener{ labels ->
            displayLabel(labels, imageUri) // Passes Uri to displayLabel
        }.addOnFailureListener { e->
            labelText.text = "Error: ${e.message}"
        }
    }

    private fun displayLabel(labels: List<ImageLabel> , imageUri: Uri?) {
        // Store the Uri for the feedback button to use later
        currentImageUri = imageUri

        if (labels.isNotEmpty()) {
            val mostConfidentLabel = labels[0]
            val labelTextValue = mostConfidentLabel.text
            val confidenece = String.format("Most Confidence level : %.2f%%" , mostConfidentLabel.confidence*100)
            labelText.text = "$labelTextValue\n$confidenece"
        } else {
            labelText.text = "No labels found"
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap) : Uri? {

        try {
            val cacheDir = File(cacheDir, "temp_images")
            cacheDir.mkdirs()
            val file = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG,90,out)
                out.flush()
            }
            return Uri.fromFile(file)
        } catch (e: Exception){
            e.printStackTrace()
            return null

        }

    }

    // Add this function definition back into your MainActivity class
    private fun saveFeedbackToPrefs(imageUri: Uri, correctLabel: String) {
        try {
            // Store the temporary Uri string instead of a local file path.
            val imageUriString = imageUri.toString()

            // Create a new Feedback Entry object
            val newEntry = FeedbackEntry(
                imageFilePath = imageUriString, // Storing the Uri string
                correctLabel = correctLabel,
                timestamp = System.currentTimeMillis()
            )

            // Load existing data from SharedPreferences
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val existingJson = prefs.getString(KEY_FEEDBACK_LIST, null)

            val feedbackListType = object : TypeToken<MutableList<FeedbackEntry>>() {}.type
            val existingList: MutableList<FeedbackEntry> =
                if (existingJson != null) {
                    gson.fromJson(existingJson, feedbackListType)
                } else {
                    mutableListOf()
                }

            // Add the new entry and save back to SharedPreferences
            existingList.add(newEntry)
            val newJson = gson.toJson(existingList)

            prefs.edit()
                .putString(KEY_FEEDBACK_LIST, newJson)
                .apply()

            // Success: Show the Confirmation Dialog (This is your desired pop-up!)
            showFeedbackSubmittedDialog()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save feedback: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 1)
        }
    }
}
