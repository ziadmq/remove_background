package com.editpictures.ziadmq

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.editpictures.ziadmq.ui.screens.EditorScreen
import com.editpictures.ziadmq.ui.screens.HomeScreen
import com.google.android.gms.ads.MobileAds
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}

        setContent {
            // State to hold the picked image
            var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

            if (selectedBitmap == null) {
                // 1. Show Home Screen if no image selected
                HomeScreen(
                    onImagePicked = { bitmap ->
                        selectedBitmap = bitmap
                    }
                )
            } else {
                // 2. Show Editor Screen if image exists
                EditorScreen(
                    initialBitmap = selectedBitmap!!,
                    onBackClick = {
                        // Go back to Home
                        selectedBitmap = null
                    },
                    onSaveClick = { bitmapToSave ->
                        saveBitmap(bitmapToSave)
                    }
                )
            }
        }
    }

    // Function to save image to Gallery
    private fun saveBitmap(bitmap: Bitmap) {
        val filename = "removed_bg_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var success = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/RemoveBackground")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val image = java.io.File(imagesDir, filename)
                fos = java.io.FileOutputStream(image)
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                success = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (success) {
            Toast.makeText(this, "Image Saved to Gallery!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }
}