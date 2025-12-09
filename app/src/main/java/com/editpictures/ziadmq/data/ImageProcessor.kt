package com.editpictures.ziadmq.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.editpictures.ziadmq.ml.Rembg
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class ImageProcessor(private val context: Context) {

    // 1. Google ML Kit Segmenter
    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()
    private val segmenter = SubjectSegmentation.getClient(options)

    // 2. Custom TFLite Segmenter (Fallback or Alternative)
    private val rembg by lazy { Rembg(context) }

    suspend fun removeBackground(input: Bitmap): Bitmap {
        return try {
            // Try Google ML Kit first
            val image = InputImage.fromBitmap(input, 0)
            val result = segmenter.process(image).await()

            result.foregroundBitmap ?: run {
                Log.w("ImageProcessor", "ML Kit returned null, trying Rembg...")
                rembg.removeBackground(input)
            }
        } catch (e: Exception) {
            Log.e("ImageProcessor", "ML Kit failed: ${e.message}. Switching to Rembg TFLite.")
            try {
                rembg.removeBackground(input)
            } catch (rembgError: Exception) {
                Log.e("ImageProcessor", "Rembg also failed: ${rembgError.message}")
                input // Return original if both fail
            }
        }
    }
}