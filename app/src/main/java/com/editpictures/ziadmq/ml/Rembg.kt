package com.editpictures.ziadmq.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class Rembg(private val context: Context) {

    // Ensure this matches your model's input size (common is 320 or 512)
    private val inputSize = 320
    private val modelPath = "rembg.tflite" // Place this file in src/main/assets/

    private val interpreter: Interpreter? by lazy {
        try {
            val options = Interpreter.Options()
            options.setNumThreads(4)
            Interpreter(loadModelFile(), options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun removeBackground(bitmap: Bitmap): Bitmap {
        val tflite = interpreter ?: return bitmap

        // 1. Preprocess
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // 2. Run Inference
        // Output shape depends on model: often [1, 320, 320, 1] or [1, 1, 320, 320]
        // Assuming [1, 320, 320, 1] for typical U2Net
        val outputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        tflite.run(inputBuffer, outputBuffer)

        // 3. Postprocess
        return applyMask(bitmap, outputBuffer)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in intValues) {
            // Normalize to 0..1 or -1..1 depending on model training.
            // Standard Rembg/U2Net usually expects standard RGB 0..1 normalization
            byteBuffer.putFloat(Color.red(pixelValue) / 255.0f)
            byteBuffer.putFloat(Color.green(pixelValue) / 255.0f)
            byteBuffer.putFloat(Color.blue(pixelValue) / 255.0f)
        }
        return byteBuffer
    }

    private fun applyMask(original: Bitmap, outputBuffer: ByteBuffer): Bitmap {
        outputBuffer.rewind()
        val width = original.width
        val height = original.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        original.getPixels(pixels, 0, width, 0, 0, width, height)

        // Read mask from buffer into an array
        val maskData = FloatArray(inputSize * inputSize)
        outputBuffer.asFloatBuffer().get(maskData)

        // Resize mask to fit original image (Nearest Neighbor for speed, Bilinear for quality)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Map (x, y) to mask coordinates
                val maskX = (x * inputSize) / width
                val maskY = (y * inputSize) / height
                val maskIndex = maskY * inputSize + maskX

                // Get prediction (sigmoid output usually 0..1)
                val confidence = maskData[maskIndex]

                // Get original pixel
                val pixel = pixels[y * width + x]

                // Set alpha based on confidence
                val alpha = (confidence * 255).toInt().coerceIn(0, 255)

                // If alpha is high enough, keep the pixel, else transparent
                pixels[y * width + x] = Color.argb(alpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}