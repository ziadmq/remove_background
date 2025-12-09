package com.editpictures.ziadmq.data

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class ImageProcessor(private val context: Context) {

    // Create the options once. We enable the foreground bitmap so ML Kit gives us the cut-out image directly.
    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    suspend fun removeBackground(input: Bitmap): Bitmap {
        // Prepare the input image for ML Kit
        val image = InputImage.fromBitmap(input, 0)

        // Process the image
        val result = segmenter.process(image).await()

        // Return the foreground bitmap (the image with background removed)
        // If it fails to get a foreground, it returns the original image as a fallback
        return result.foregroundBitmap ?: input
    }
}

