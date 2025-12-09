package com.editpictures.ziadmq.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // IMPORT REQUIRED for 'by'
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.editpictures.ziadmq.ui.viewmodel.BgViewModel

@Composable
fun HomeScreen(viewModel: BgViewModel, onDone: (Bitmap) -> Unit) {

    // 1. Get Context safely outside the callback
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // 2. Safe Image Loading (Fixes the crash)
                val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                // 3. Send to ViewModel
                viewModel.startRemove(bitmap)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 4. Observe the state properly
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(20.dp))
        Button(onClick = { launcher.launch("image/*") }) {
            Text("Choose Image")
        }

        Spacer(Modifier.height(20.dp))

        when (state) {
            is BgViewModel.UiState.Loading -> CircularProgressIndicator()
            is BgViewModel.UiState.Success -> {
                val output = (state as BgViewModel.UiState.Success).bitmap
                onDone(output)
            }
            is BgViewModel.UiState.Error -> {
                // Show the specific error message from the ViewModel
                val errorMsg = (state as BgViewModel.UiState.Error).message
                Text("Error: $errorMsg")
            }
            else -> Unit
        }
    }
}