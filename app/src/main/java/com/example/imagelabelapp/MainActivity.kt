package com.example.imagelabelapp

import android.app.Activity
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var objectImage: ImageView
    private lateinit var labelText: TextView
    private lateinit var captureImgBtn: Button
    private lateinit var galleryBtn: Button
    private lateinit var feedbackText: EditText
    private lateinit var feedbackButton: Button

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

        // 2. Setup
        checkCameraPermission()
        imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        // 3. Register Activity Launchers
        // Handles image captured from Camera (returns Bitmap, needs conversion to Uri)
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val extras = result.data?.extras
                val imageBitmap = extras?.getParcelable("data", Bitmap::class.java)
                if (imageBitmap != null) {
                    val imageUri = getImageUri(imageBitmap) // Helper function to get Uri
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

        // Fix: Correct setOnClickListener syntax and implementation for feedback
        feedbackButton.setOnClickListener {
            val correctLabel = feedbackText.text.toString().trim()
            if (currentImageUri != null && correctLabel.isNotEmpty()) {
                saveFeedback(currentImageUri!!, correctLabel)
                feedbackText.text.clear()
            } else {
                Toast.makeText(this, "Please capture an image and enter a label.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- ML KIT LOGIC ---

    // The single, correct definition of labelImage
    private fun labelImage(bitmap: Bitmap , imageUri: Uri?) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        imageLabeler.process(inputImage).addOnSuccessListener{ labels ->
            displayLabel(labels, imageUri) // Passes Uri to displayLabel
        }.addOnFailureListener { e->
            labelText.text = "Error: ${e.message}"
        }
    }

    // Corrected displayLabel function signature
    private fun displayLabel(labels: List<ImageLabel> , imageUri: Uri?) {
        // Store the Uri for the feedback button to use later
        currentImageUri = imageUri

        if (labels.isNotEmpty()) {
            val mostConfidentLabel = labels[0]
            // Optional: Include confidence for better user experience
            val confidence = String.format("%.2f%%", mostConfidentLabel.confidence * 100)
            labelText.text = "${mostConfidentLabel.text} ($confidence)"
        } else {
            labelText.text = "No labels found"
        }
    }

    // --- FEEDBACK STORAGE LOGIC (Internal Storage) ---

    // Helper function to get Uri from Bitmap (needed for camera captures)
    private fun getImageUri(bitmap: Bitmap): Uri? {
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        // Note: Using the deprecated insertImage for simplicity, requires WRITE_EXTERNAL_STORAGE on older APIs
        // but for app-internal Uris, it generally works to get a path.
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "ImageLabel", null)
        return if (path != null) Uri.parse(path) else null
    }

    private fun saveFeedback(imageUri: Uri, correctLabel: String) {
        try {
            // 1. Generate a unique ID for this feedback pair
            val uniqueId = UUID.randomUUID().toString()

            // --- A. Save the Image File (Copy from Uri to internal storage) ---

            val imageFileName = "$uniqueId.jpg"
            val imageFile = File(filesDir, imageFileName) // filesDir points to internal storage

            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val outputStream: FileOutputStream = FileOutputStream(imageFile)

            // Efficiently copy the stream, ensuring both streams are closed afterwards
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val labelFileName = "$uniqueId.txt"

            openFileOutput(labelFileName, Context.MODE_PRIVATE).use {
                it.write(correctLabel.toByteArray())
            }

            Toast.makeText(this, "Feedback saved as: $uniqueId", Toast.LENGTH_LONG).show()

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
