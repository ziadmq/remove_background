package com.editpictures.ziadmq.data

import android.content.Context
import android.graphics.Bitmap
import com.naishe.rembg.Rembg

class ImageProcessor(private val context: Context) {

    private val rembg by lazy {
        Rembg(context)
    }

    suspend fun removeBackground(input: Bitmap): Bitmap {
        return rembg.removeBackground(input)
    }
}
