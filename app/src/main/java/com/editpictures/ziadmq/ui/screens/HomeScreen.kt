package com.editpictures.ziadmq.ui.screens

import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.editpictures.ziadmq.ui.viewmodel.BgViewModel

@Composable
fun HomeScreen(viewModel: BgViewModel, onDone: (Bitmap) -> Unit) {

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = MediaStore.Images.Media.getBitmap(
                LocalContext.current.contentResolver,
                uri
            )
            viewModel.startRemove(bitmap)
        }
    }

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
            is BgViewModel.UiState.Error -> Text("Error")
            else -> Unit
        }
    }
}
