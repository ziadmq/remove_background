package com.editpictures.ziadmq.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
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
    private val MAX_HISTORY = 10

    // --- NEW: Magic Eraser Variables ---
    private var magicBrushTargetColor: Int = 0

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

    // --- AUTO AI REMOVAL ---
    fun autoRemoveBackground() {
        val current = _currentBitmap.value ?: return
        saveToHistory()
        viewModelScope.launch {
            _isLoading.value = true
            // Run on IO thread to prevent UI freezing
            val result = withContext(Dispatchers.IO) { imageProcessor.removeBackground(current) }
            _currentBitmap.value = result.copy(Bitmap.Config.ARGB_8888, true)
            _isLoading.value = false
        }
    }

    // --- MANUAL ERASE / RESTORE ---
    fun applyManualBrush(x: Float, y: Float, size: Float, isEraser: Boolean) {
        val current = _currentBitmap.value ?: return

        val canvas = Canvas(current)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            strokeWidth = size
        }

        if (isEraser) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawCircle(x, y, size / 2, paint)
        } else {
            originalBitmap?.let { orig ->
                paint.shader = BitmapShader(orig, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                canvas.drawCircle(x, y, size / 2, paint)
            }
        }

        _currentBitmap.value = current
    }

    // --- MAGIC ERASER BRUSH (NEW) ---
    // 1. Call this when the user starts touching (onDragStart)
    fun initMagicBrush(x: Float, y: Float) {
        val bitmap = _currentBitmap.value ?: return
        if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
            // Remember the color we want to erase (the background color)
            magicBrushTargetColor = bitmap.getPixel(x.toInt(), y.toInt())
        }
    }

    // 2. Call this while the user drags (onDrag)
    fun applyMagicBrush(x: Float, y: Float, size: Float, tolerance: Float) {
        val bitmap = _currentBitmap.value ?: return
        val w = bitmap.width
        val h = bitmap.height
        val cx = x.toInt()
        val cy = y.toInt()
        val r = (size / 2).toInt() // Brush Radius
        val rSq = r * r

        // Calculate the square area of the brush to speed up processing
        val minX = (cx - r).coerceAtLeast(0)
        val maxX = (cx + r).coerceAtMost(w - 1)
        val minY = (cy - r).coerceAtLeast(0)
        val maxY = (cy + r).coerceAtMost(h - 1)

        val areaW = maxX - minX + 1
        val areaH = maxY - minY + 1
        if (areaW <= 0 || areaH <= 0) return

        // Get pixels just for the brush area
        val pixels = IntArray(areaW * areaH)
        bitmap.getPixels(pixels, 0, areaW, minX, minY, areaW, areaH)

        // Prepare colors for comparison
        val refR = android.graphics.Color.red(magicBrushTargetColor)
        val refG = android.graphics.Color.green(magicBrushTargetColor)
        val refB = android.graphics.Color.blue(magicBrushTargetColor)
        // Convert tolerance (0-100) to a distance threshold
        val threshold = (tolerance * 2.55f) * (tolerance * 2.55f) * 3

        var hasChanges = false

        for (i in 0 until areaH) {
            for (j in 0 until areaW) {
                val px = minX + j
                val py = minY + i

                // 1. Check if pixel is inside the brush circle
                val distSq = (px - cx) * (px - cx) + (py - cy) * (py - cy)
                if (distSq <= rSq) {
                    val pixelColor = pixels[i * areaW + j]

                    // 2. Skip if already transparent
                    if (pixelColor != 0) {
                        val pR = android.graphics.Color.red(pixelColor)
                        val pG = android.graphics.Color.green(pixelColor)
                        val pB = android.graphics.Color.blue(pixelColor)

                        // 3. Check if color matches the target (Background)
                        val diff = (refR - pR) * (refR - pR) +
                                (refG - pG) * (refG - pG) +
                                (refB - pB) * (refB - pB)

                        if (diff <= threshold) {
                            pixels[i * areaW + j] = 0 // Erase this pixel
                            hasChanges = true
                        }
                    }
                }
            }
        }

        // Apply changes back to the bitmap
        if (hasChanges) {
            bitmap.setPixels(pixels, 0, areaW, minX, minY, areaW, areaH)
            _currentBitmap.value = bitmap // Trigger screen update
        }
    }

    // --- NEW TOOL: LASSO (Freehand Selection) ---
    fun applyLasso(points: List<Offset>, scale: Float, offsetX: Float, offsetY: Float, canvasWidth: Int, canvasHeight: Int) {
        val current = _currentBitmap.value ?: return
        saveToHistory()

        val canvas = Canvas(current)
        val path = Path()

        // 1. Convert Screen Coordinates to Bitmap Coordinates
        if (points.isNotEmpty()) {
            val first = points.first()
            val startX = (first.x - (canvasWidth / 2 + offsetX)) / scale + current.width / 2
            val startY = (first.y - (canvasHeight / 2 + offsetY)) / scale + current.height / 2
            path.moveTo(startX, startY)

            for (point in points) {
                val bx = (point.x - (canvasWidth / 2 + offsetX)) / scale + current.width / 2
                val by = (point.y - (canvasHeight / 2 + offsetY)) / scale + current.height / 2
                path.lineTo(bx, by)
            }
            path.close()
        }

        // 2. Cut out the shape
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Removes pixels inside
        }

        canvas.drawPath(path, paint)
        _currentBitmap.value = current
    }

    // --- MAGIC WAND (FLOOD FILL) ---
    fun magicRemove(startX: Int, startY: Int, tolerance: Float) {
        val bitmap = _currentBitmap.value ?: return
        if (startX < 0 || startX >= bitmap.width || startY < 0 || startY >= bitmap.height) return

        saveToHistory()

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

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetColor = pixels[y * width + x]
        if (android.graphics.Color.alpha(targetColor) == 0) return result

        val queue: Queue<Int> = LinkedList()
        queue.add(y * width + x)

        val visited = BooleanArray(width * height)
        visited[y * width + x] = true

        val tRed = android.graphics.Color.red(targetColor)
        val tGreen = android.graphics.Color.green(targetColor)
        val tBlue = android.graphics.Color.blue(targetColor)

        // Tolerance calculation
        val threshold = (tolerance * 2.55f) * (tolerance * 2.55f) * 3

        while (!queue.isEmpty()) {
            val index = queue.poll() ?: continue
            pixels[index] = 0 // Set to transparent

            val px = index % width
            // 4-Way Connectivity
            val neighbors = intArrayOf(index - width, index + width, index - 1, index + 1)

            for (nIndex in neighbors) {
                if (nIndex in pixels.indices && !visited[nIndex]) {
                    val nx = nIndex % width
                    if (abs(nx - px) > 1) continue // Wrap check

                    val nColor = pixels[nIndex]
                    if (nColor != 0) {
                        val nRed = android.graphics.Color.red(nColor)
                        val nGreen = android.graphics.Color.green(nColor)
                        val nBlue = android.graphics.Color.blue(nColor)

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

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}