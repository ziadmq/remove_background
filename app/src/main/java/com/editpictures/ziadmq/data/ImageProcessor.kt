package com.editpictures.ziadmq.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class ImageProcessor(private val context: Context) {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    suspend fun removeBackground(input: Bitmap): Bitmap {
        return try {
            val image = InputImage.fromBitmap(input, 0)

            // This is where it was crashing!
            val result = segmenter.process(image).await()

            result.foregroundBitmap ?: input
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error removing background: ${e.message}")
            e.printStackTrace()
            input // Return the original image if it fails
        }
    }
}