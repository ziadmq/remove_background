package com.editpictures.ziadmq

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.editpictures.ziadmq.data.ImageProcessor // <--- THIS IMPORT WAS MISSING
import com.editpictures.ziadmq.ui.screens.HomeScreen
import com.editpictures.ziadmq.ui.screens.ResultScreen
import com.editpictures.ziadmq.ui.viewmodel.BgViewModel
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import com.editpictures.ziadmq.ui.screens.EditorScreen
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the processor
        val processor = ImageProcessor(this)
        // Initialize the ViewModel
        val viewModel = BgViewModel(processor)

        setContent {
//            var output by remember { mutableStateOf<Bitmap?>(null) }
//
//            if (output == null) {
//                HomeScreen(viewModel) { bitmap ->
//                    output = bitmap
//                }
//            } else {
//                ResultScreen(output!!) {
//                    saveBitmap(output!!)
//                }
//            }
            val dummyBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_background)

            setContent {
                // You don't need a theme wrapper if you hardcoded colors,
                // but normally you wrap this in your AppTheme
                EditorScreen(
                    initialBitmap = dummyBitmap,
                    onBackClick = { finish() }
                )
            }
        }
    }

    fun saveBitmap(bitmap: Bitmap) {
        val filename = "removed_bg_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/RemoveBackground")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = resolver.openOutputStream(imageUri!!)
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val image = java.io.File(imagesDir, filename)
                fos = java.io.FileOutputStream(image)
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}