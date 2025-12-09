package com.editpictures.ziadmq.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.editpictures.ziadmq.ui.viewmodel.EditorViewModel
import java.io.File
import java.io.FileOutputStream

enum class EditorTool(val label: String, val icon: ImageVector) {
    PAN_ZOOM("Zoom", Icons.Default.PanTool),
    CROP("Crop", Icons.Default.Crop),
    MAGIC("Auto Color", Icons.Default.Colorize),
    ERASE("Eraser", Icons.Default.CleaningServices),
    RESTORE("Restore", Icons.Default.Brush),
    AUTO("Auto AI", Icons.Default.AutoFixHigh),
    BG_CHECK("View", Icons.Default.Visibility)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialBitmap: Bitmap,
    onBackClick: () -> Unit,
    onSaveClick: (Bitmap) -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    LaunchedEffect(initialBitmap) { viewModel.loadImage(initialBitmap) }

    val currentBitmap by viewModel.currentBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current // For Haptic Feedback

    // State
    var selectedTool by remember { mutableStateOf(EditorTool.PAN_ZOOM) }
    var brushSize by remember { mutableFloatStateOf(60f) }
    var tolerance by remember { mutableFloatStateOf(30f) }
    var bgMode by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.DarkGray, Color.Black, Color.White)

    // Canvas Transform
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // UX State
    var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }
    var showCursor by remember { mutableStateOf(false) }
    val touchOffset = 150f // Higher offset so finger doesn't hide the magnifier

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val cropped = BitmapFactory.decodeStream(stream)
                    viewModel.loadImage(cropped)
                    scale = 1f; offset = Offset.Zero; selectedTool = EditorTool.PAN_ZOOM
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.undo()
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) // Feedback
                    }) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }

                    IconButton(onClick = {
                        viewModel.redo()
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }) { Icon(Icons.Default.Redo, "Redo", tint = Color.White) }

                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { currentBitmap?.let { onSaveClick(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) { Text("SAVE", color = Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(
                        0xFF121212
                    )
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier
                .background(Color(0xFF1E1E1E))
                .padding(bottom = 8.dp)) {
                if (selectedTool == EditorTool.ERASE || selectedTool == EditorTool.RESTORE) {
                    SliderControl("Size", brushSize, 20f..200f) { brushSize = it }
                }
                if (selectedTool == EditorTool.MAGIC) {
                    SliderControl("Tolerance", tolerance, 1f..100f) { tolerance = it }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorTool.values().forEach { tool ->
                        ToolButton(
                            tool = tool,
                            isSelected = selectedTool == tool,
                            onClick = {
                                when (tool) {
                                    EditorTool.AUTO -> {
                                        viewModel.autoRemoveBackground()
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }

                                    EditorTool.BG_CHECK -> bgMode = (bgMode + 1) % 3
                                    EditorTool.CROP -> {
                                        currentBitmap?.let { bmp ->
                                            val uri = saveBitmapToCache(context, bmp)
                                            val options = CropImageOptions(
                                                toolbarColor = Color(0xFF121212).toArgb(),
                                                toolbarTitleColor = Color.White.toArgb(),
                                                activityMenuIconColor = Color.White.toArgb(),
                                                outputRequestWidth = 1080,
                                                outputRequestHeight = 1920,
                                                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE
                                            )
                                            cropLauncher.launch(
                                                CropImageContractOptions(
                                                    uri,
                                                    options
                                                )
                                            )
                                        }
                                    }

                                    else -> selectedTool = tool
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColors[bgMode])
                .clip(androidx.compose.ui.graphics.RectangleShape)
        ) {
            if (currentBitmap != null) {
                val bitmap = currentBitmap!!
                val original = viewModel.originalBitmap

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        // REMOVED .magnifier() to fix your error
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (selectedTool == EditorTool.PAN_ZOOM) {
                                    scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                                    offset += pan
                                }
                            }
                        }
                        .pointerInput(selectedTool, brushSize, tolerance) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue

                                    // Custom Logic for Cursor
                                    if (event.changes.any { it.pressed }) {
                                        magnifierCenter = change.position
                                        showCursor = true
                                    } else {
                                        showCursor = false
                                    }

                                    // TOUCH DOWN
                                    if (change.pressed && change.previousPressed.not()) {
                                        if (selectedTool == EditorTool.ERASE || selectedTool == EditorTool.RESTORE) {
                                            viewModel.saveToHistory()
                                        }
                                    }

                                    // TOUCH UP (Magic Wand)
                                    if (change.changedToUp() && selectedTool == EditorTool.MAGIC) {
                                        val aimX = change.position.x
                                        val aimY = change.position.y - touchOffset
                                        val bmpX =
                                            ((aimX - offset.x - size.width / 2) / scale + bitmap.width / 2).toInt()
                                        val bmpY =
                                            ((aimY - offset.y - size.height / 2) / scale + bitmap.height / 2).toInt()
                                        viewModel.magicRemove(bmpX, bmpY, tolerance)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }

                                    // DRAG
                                    if (event.changes.any { it.pressed }) {
                                        val touchX = change.position.x
                                        val touchY = change.position.y - touchOffset
                                        val bmpX =
                                            (touchX - offset.x - size.width / 2) / scale + bitmap.width / 2
                                        val bmpY =
                                            (touchY - offset.y - size.height / 2) / scale + bitmap.height / 2

                                        if (selectedTool == EditorTool.ERASE || selectedTool == EditorTool.RESTORE) {
                                            applyEdit(
                                                touchX = bmpX, touchY = bmpY,
                                                tool = selectedTool, brushSize = brushSize / scale,
                                                bitmap = bitmap, original = original,
                                                onUpdate = { viewModel.updateBitmap(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    with(drawContext.canvas.nativeCanvas) {
                        save()
                        translate(canvasWidth / 2 + offset.x, canvasHeight / 2 + offset.y)
                        scale(scale, scale)
                        translate(-bitmap.width / 2f, -bitmap.height / 2f)
                        drawBitmap(bitmap, 0f, 0f, null)
                        restore()
                    }

                    // --- MANUAL MAGNIFIER (Custom Drawing) ---
                    if (showCursor) {
                        val targetX = magnifierCenter.x
                        val targetY = magnifierCenter.y - touchOffset

                        // 1. Draw Crosshair Target
                        drawCircle(
                            Color(0xFFBB86FC),
                            radius = 20f,
                            center = Offset(targetX, targetY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3f)
                        )
                        drawCircle(Color.White, radius = 4f, center = Offset(targetX, targetY))

                        // 2. Draw Zoom Bubble (The Manual Loupe)
                        // We draw a circle at the finger position containing the zoomed content
                        val bubbleCenter =
                            magnifierCenter - Offset(0f, touchOffset + 150f) // Bubble above cursor
                        val bubbleRadius = 120f

                        // Draw Background for Bubble
                        drawCircle(Color.Black, radius = bubbleRadius + 4f, center = bubbleCenter)
                        drawCircle(Color.White, radius = bubbleRadius, center = bubbleCenter)

                        // Clip to Circle for the Zoomed Image
                        clipPath(androidx.compose.ui.graphics.Path().apply {
                            addOval(
                                androidx.compose.ui.geometry.Rect(
                                    center = bubbleCenter,
                                    radius = bubbleRadius
                                )
                            )
                        }) {
                            // Draw the bitmap again, but zoomed in at the target location
                            with(drawContext.canvas.nativeCanvas) {
                                save()
                                // Move to bubble center
                                translate(bubbleCenter.x, bubbleCenter.y)
                                // Scale up (2x zoom)
                                scale(2f, 2f)
                                // Move back so the 'target' pixel is at (0,0)
                                translate(-targetX, -targetY)

                                // Re-apply the main canvas transforms to match screen space
                                translate(canvasWidth / 2 + offset.x, canvasHeight / 2 + offset.y)
                                scale(scale, scale)
                                translate(-bitmap.width / 2f, -bitmap.height / 2f)

                                drawBitmap(bitmap, 0f, 0f, null)
                                restore()
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFBB86FC)
                )
            }
        }
    }
}

// ... Keep Helper Functions (saveBitmapToCache, applyEdit, ToolButton, SliderControl) same as before ...
@Composable
fun SliderControl(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(60.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFBB86FC),
                activeTrackColor = Color(0xFFBB86FC)
            )
        )
    }
}

fun saveBitmapToCache(context: Context, bitmap: Bitmap): android.net.Uri {
    val file = File(context.cacheDir, "temp_edit_image.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return android.net.Uri.fromFile(file)
}

fun applyEdit(
    touchX: Float,
    touchY: Float,
    tool: EditorTool,
    brushSize: Float,
    bitmap: Bitmap,
    original: Bitmap?,
    onUpdate: (Bitmap) -> Unit
) {
    if (touchX < -brushSize || touchX > bitmap.width + brushSize || touchY < -brushSize || touchY > bitmap.height + brushSize) return
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    paint.maskFilter = BlurMaskFilter(brushSize * 0.1f, BlurMaskFilter.Blur.NORMAL)

    if (tool == EditorTool.ERASE) {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(touchX, touchY, brushSize / 2, paint)
    } else if (tool == EditorTool.RESTORE && original != null) {
        val shader = BitmapShader(original, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        canvas.drawCircle(touchX, touchY, brushSize / 2, paint)
    }
    onUpdate(bitmap)
}

@Composable
fun ToolButton(tool: EditorTool, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color(0xFFBB86FC) else Color.Transparent)
                .border(
                    1.dp,
                    if (isSelected) Color.Transparent else Color.Gray,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (isSelected) Color.Black else Color.White
            )
        }
        Text(
            text = tool.label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}