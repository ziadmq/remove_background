package com.editpictures.ziadmq.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.editpictures.ziadmq.data.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue
import java.util.Stack
import kotlin.math.abs
import kotlin.math.sqrt

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor(application.applicationContext)
    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()
    var originalBitmap: Bitmap? = null
        private set
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val undoStack = Stack<Bitmap>()
    private val redoStack = Stack<Bitmap>()
    private val MAX_HISTORY = 5

    fun loadImage(bitmap: Bitmap) {
        if (_currentBitmap.value == null) {
            val mutableBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            _currentBitmap.value = mutableBmp
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun saveToHistory() {
        _currentBitmap.value?.let { current ->
            if (undoStack.size >= MAX_HISTORY) undoStack.removeAt(0)
            undoStack.push(current.copy(Bitmap.Config.ARGB_8888, true))
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.pop()
            _currentBitmap.value?.let { redoStack.push(it) }
            _currentBitmap.value = previous
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val future = redoStack.pop()
            _currentBitmap.value?.let { undoStack.push(it) }
            _currentBitmap.value = future
        }
    }

    fun autoRemoveBackground() {
        val current = _currentBitmap.value ?: return
        saveToHistory()
        viewModelScope.launch {
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) { imageProcessor.removeBackground(current) }
            _currentBitmap.value = result.copy(Bitmap.Config.ARGB_8888, true)
            _isLoading.value = false
        }
    }

    fun updateBitmap(newBitmap: Bitmap) {
        _currentBitmap.value = newBitmap
    }

    // --- NEW FEATURE: MAGIC WAND (FLOOD FILL) ---
    fun magicRemove(startX: Int, startY: Int, tolerance: Float) {
        val bitmap = _currentBitmap.value ?: return
        if (startX < 0 || startX >= bitmap.width || startY < 0 || startY >= bitmap.height) return

        saveToHistory() // Save before magic

        viewModelScope.launch {
            _isLoading.value = true
            val newBitmap = withContext(Dispatchers.Default) {
                floodFill(bitmap, startX, startY, tolerance)
            }
            _currentBitmap.value = newBitmap
            _isLoading.value = false
        }
    }

    private fun floodFill(source: Bitmap, x: Int, y: Int, tolerance: Float): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetColor = pixels[y * width + x]
        if (targetColor == 0) return source // Already transparent

        val queue: Queue<Int> = LinkedList()
        queue.add(y * width + x)

        val visited = BooleanArray(width * height)
        visited[y * width + x] = true

        val tRed = Color.red(targetColor)
        val tGreen = Color.green(targetColor)
        val tBlue = Color.blue(targetColor)

        // Tolerance threshold (0-100 converted to color distance)
        val threshold = (tolerance * 2.55f) * (tolerance * 2.55f) * 3 // Squared distance approximation

        while (!queue.isEmpty()) {
            val index = queue.poll() ?: continue
            val px = index % width
            val py = index / width

            pixels[index] = 0 // ERASE PIXEL (Set to Transparent)

            // Check Neighbors (Up, Down, Left, Right)
            val neighbors = intArrayOf(
                index - width, // Up
                index + width, // Down
                index - 1,     // Left
                index + 1      // Right
            )

            for (nIndex in neighbors) {
                if (nIndex in pixels.indices && !visited[nIndex]) {
                    // Check bounds for Left/Right wrapping
                    val nx = nIndex % width
                    if (abs(nx - px) > 1) continue

                    val neighborColor = pixels[nIndex]
                    if (neighborColor != 0) { // Skip if already transparent
                        val nRed = Color.red(neighborColor)
                        val nGreen = Color.green(neighborColor)
                        val nBlue = Color.blue(neighborColor)

                        // Calculate Color Distance (Squared Euclidean)
                        val dist = (tRed - nRed) * (tRed - nRed) +
                                (tGreen - nGreen) * (tGreen - nGreen) +
                                (tBlue - nBlue) * (tBlue - nBlue)

                        if (dist <= threshold) {
                            visited[nIndex] = true
                            queue.add(nIndex)
                        }
                    }
                }
            }
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}