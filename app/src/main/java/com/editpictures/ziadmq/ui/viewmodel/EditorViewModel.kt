package com.editpictures.ziadmq.ui.viewmodel

import android.app.Application
import android.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.editpictures.ziadmq.data.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs

// تعريف أنواع الخلفيات المتاحة
sealed class BackgroundType {
    object Transparent : BackgroundType()
    data class Color(val color: Int) : BackgroundType()
    data class Gradient(val colors: IntArray) : BackgroundType()
    data class Image(val bitmap: Bitmap) : BackgroundType()
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val imageProcessor = ImageProcessor(application.applicationContext)
    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()

    // حالات الميزات الجديدة
    private val _backgroundType = MutableStateFlow<BackgroundType>(BackgroundType.Transparent)
    val backgroundType = _backgroundType.asStateFlow()

    private val _edgeSmoothing = MutableStateFlow(0f)
    val edgeSmoothing = _edgeSmoothing.asStateFlow()

    private val _shadowIntensity = MutableStateFlow(0f)
    val shadowIntensity = _shadowIntensity.asStateFlow()

    var originalBitmap: Bitmap? = null
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val undoStack = Stack<Bitmap>()
    private val redoStack = Stack<Bitmap>()
    private val MAX_HISTORY = 10
    private var magicBrushTargetColor: Int = 0

    fun loadImage(bitmap: Bitmap) {
        if (_currentBitmap.value == null) {
            val mutableBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            _currentBitmap.value = mutableBmp
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    // دوال التحكم في الحالات
    fun setBackground(type: BackgroundType) { _backgroundType.value = type }
    fun updateEdgeSmoothing(value: Float) { _edgeSmoothing.value = value }
    fun updateShadowIntensity(value: Float) { _shadowIntensity.value = value }

    // دالة دمج كل الطبقات (الخلفية + الظل + الصورة المنعمة) للحفظ النهائي
    fun getCompositedBitmap(): Bitmap? {
        val foreground = _currentBitmap.value ?: return null
        val result = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. رسم الخلفية المختارة
        when (val bg = _backgroundType.value) {
            is BackgroundType.Color -> canvas.drawColor(bg.color)
            is BackgroundType.Gradient -> {
                val shader = LinearGradient(0f, 0f, 0f, result.height.toFloat(), bg.colors, null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), Paint().apply { this.shader = shader })
            }
            is BackgroundType.Image -> {
                val scaledBg = Bitmap.createScaledBitmap(bg.bitmap, result.width, result.height, true)
                canvas.drawBitmap(scaledBg, 0f, 0f, null)
            }
            else -> {} // شفاف
        }

        // 2. رسم الظل الذكي
        val sInt = _shadowIntensity.value
        if (sInt > 0f) {
            val shadowPaint = Paint().apply {
                colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
                alpha = (sInt * 3).toInt().coerceIn(0, 150)
                maskFilter = BlurMaskFilter(sInt + 1f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(foreground, 15f, 15f, shadowPaint)
        }

        // 3. رسم الصورة مع تنعيم الحواف
        val smooth = _edgeSmoothing.value
        val mainPaint = Paint().apply {
            if (smooth > 0f) maskFilter = BlurMaskFilter(smooth, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(foreground, 0f, 0f, mainPaint)

        return result
    }

    // --- الوظائف الأساسية للمحرر ---
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

    fun applyManualBrush(x: Float, y: Float, size: Float, isEraser: Boolean) {
        val current = _currentBitmap.value ?: return
        val canvas = Canvas(current)
        val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; strokeWidth = size }
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

    fun initMagicBrush(x: Float, y: Float) {
        val bitmap = _currentBitmap.value ?: return
        if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) magicBrushTargetColor = bitmap.getPixel(x.toInt(), y.toInt())
    }

    fun applyMagicBrush(x: Float, y: Float, size: Float, tolerance: Float) {
        val bitmap = _currentBitmap.value ?: return
        val w = bitmap.width; val h = bitmap.height; val cx = x.toInt(); val cy = y.toInt()
        val r = (size / 2).toInt(); val rSq = r * r
        val minX = (cx - r).coerceAtLeast(0); val maxX = (cx + r).coerceAtMost(w - 1)
        val minY = (cy - r).coerceAtLeast(0); val maxY = (cy + r).coerceAtMost(h - 1)
        val areaW = maxX - minX + 1; val areaH = maxY - minY + 1
        if (areaW <= 0 || areaH <= 0) return
        val pixels = IntArray(areaW * areaH)
        bitmap.getPixels(pixels, 0, areaW, minX, minY, areaW, areaH)
        val refR = Color.red(magicBrushTargetColor); val refG = Color.green(magicBrushTargetColor); val refB = Color.blue(magicBrushTargetColor)
        val threshold = (tolerance * 2.55f) * (tolerance * 2.55f) * 3
        var hasChanges = false
        for (i in 0 until areaH) {
            for (j in 0 until areaW) {
                val px = minX + j; val py = minY + i
                val distSq = (px - cx) * (px - cx) + (py - cy) * (py - cy)
                if (distSq <= rSq) {
                    val pixelColor = pixels[i * areaW + j]
                    if (pixelColor != 0) {
                        val pR = Color.red(pixelColor); val pG = Color.green(pixelColor); val pB = Color.blue(pixelColor)
                        val diff = (refR - pR) * (refR - pR) + (refG - pG) * (refG - pG) + (refB - pB) * (refB - pB)
                        if (diff <= threshold) { pixels[i * areaW + j] = 0; hasChanges = true }
                    }
                }
            }
        }
        if (hasChanges) { bitmap.setPixels(pixels, 0, areaW, minX, minY, areaW, areaH); _currentBitmap.value = bitmap }
    }

    fun applyLasso(points: List<Offset>, scale: Float, offsetX: Float, offsetY: Float, canvasWidth: Int, canvasHeight: Int) {
        val current = _currentBitmap.value ?: return
        saveToHistory()
        val canvas = Canvas(current)
        val path = Path()
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
        canvas.drawPath(path, Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        _currentBitmap.value = current
    }

    fun magicRemove(startX: Int, startY: Int, tolerance: Float) {
        val bitmap = _currentBitmap.value ?: return
        if (startX < 0 || startX >= bitmap.width || startY < 0 || startY >= bitmap.height) return
        saveToHistory()
        viewModelScope.launch {
            _isLoading.value = true
            val newBitmap = withContext(Dispatchers.Default) { floodFill(bitmap, startX, startY, tolerance) }
            _currentBitmap.value = newBitmap
            _isLoading.value = false
        }
    }

    private fun floodFill(source: Bitmap, x: Int, y: Int, tolerance: Float): Bitmap {
        val width = source.width; val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        val targetColor = pixels[y * width + x]
        if (Color.alpha(targetColor) == 0) return result
        val queue: Queue<Int> = LinkedList(); queue.add(y * width + x)
        val visited = BooleanArray(width * height); visited[y * width + x] = true
        val tRed = Color.red(targetColor); val tGreen = Color.green(targetColor); val tBlue = Color.blue(targetColor)
        val threshold = (tolerance * 2.55f) * (tolerance * 2.55f) * 3
        while (!queue.isEmpty()) {
            val index = queue.poll() ?: continue
            pixels[index] = 0
            val px = index % width
            val neighbors = intArrayOf(index - width, index + width, index - 1, index + 1)
            for (nIndex in neighbors) {
                if (nIndex in pixels.indices && !visited[nIndex]) {
                    val nx = nIndex % width
                    if (abs(nx - px) > 1) continue
                    val nColor = pixels[nIndex]
                    if (nColor != 0) {
                        val nRed = Color.red(nColor); val nGreen = Color.green(nColor); val nBlue = Color.blue(nColor)
                        val diff = (tRed - nRed) * (tRed - nRed) + (tGreen - nGreen) * (tGreen - nGreen) + (tBlue - nBlue) * (tBlue - nBlue)
                        if (diff <= threshold) { visited[nIndex] = true; queue.add(nIndex) }
                    }
                }
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}