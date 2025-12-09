package com.editpictures.ziadmq.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.editpictures.ziadmq.data.ImageProcessor // Your existing class
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

val DarkBg = Color(0xFF121212)
val SurfaceBg = Color(0xFF1E1E1E)
val PrimaryPurple = Color(0xFFBB86FC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialBitmap: Bitmap?, // Pass the image from MainActivity
    onBackClick: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    // Load the initial image into ViewModel once when screen starts
    LaunchedEffect(initialBitmap) {
        initialBitmap?.let { viewModel.loadImage(it) }
    }

    // Observe State
    val currentBitmap by viewModel.currentImage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Editor", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { /* TODO: Save Logic */ }) {
                        Text("SAVE", color = PrimaryPurple, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBg)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { viewModel.removeBackground() },
                    enabled = !isLoading, // Disable button while loading
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryPurple,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove BG")
                }
            }
        }
    ) { paddingValues ->

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // The Image Card
            if (currentBitmap != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "Edited Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Text("No Image Selected", color = Color.Gray)
            }

            // Loading Spinner Overlay
            if (isLoading) {
                CircularProgressIndicator(color = PrimaryPurple)
            }
        }
    }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor(application.applicationContext)

    // STATE: Holds the current image being displayed
    private val _currentImage = MutableStateFlow<Bitmap?>(null)
    val currentImage = _currentImage.asStateFlow()

    // STATE: Are we currently processing?
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun loadImage(bitmap: Bitmap) {
        _currentImage.value = bitmap
    }

    fun removeBackground() {
        val original = _currentImage.value ?: return

        viewModelScope.launch {
            _isLoading.value = true // Start Loading

            // Run ML Kit on IO thread
            val newBitmap = withContext(Dispatchers.IO) {
                imageProcessor.removeBackground(original)
            }

            _currentImage.value = newBitmap // Update Image
            _isLoading.value = false // Stop Loading
        }
    }
}