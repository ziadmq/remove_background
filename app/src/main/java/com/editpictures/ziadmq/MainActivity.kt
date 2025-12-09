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
import com.editpictures.ziadmq.ui.screens.HomeScreen
import com.editpictures.ziadmq.ui.screens.ResultScreen
import com.editpictures.ziadmq.ui.viewmodel.BgViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val processor = ImageProcessor(this)
        val viewModel = BgViewModel(processor)

        setContent {
            var output by remember { mutableStateOf<Bitmap?>(null) }

            if (output == null) {
                }
            } else {
                ResultScreen(output!!) {
                    saveBitmap(output!!)
                }
            }
        }
    }
            val filename = "removed_bg_${System.currentTimeMillis()}.png"
            val fos = openFileOutput(filename, Context.MODE_PRIVATE)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
        }
    }