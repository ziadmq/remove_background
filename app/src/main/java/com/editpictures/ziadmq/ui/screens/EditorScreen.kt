package com.editpictures.ziadmq.ui.screens

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    AUTO("Auto AI", Icons.Default.AutoFixHigh),
    MAGIC("Magic", Icons.Default.Colorize),
    ERASE("Erase", Icons.Default.CleaningServices),
    RESTORE("Restore", Icons.Default.Brush),
    LASSO("Lasso", Icons.Default.Gesture),
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
    val view = LocalView.current

    // Tools & Settings
    var selectedTool by remember { mutableStateOf(EditorTool.PAN_ZOOM) }
    var brushSize by remember { mutableFloatStateOf(60f) }
    var tolerance by remember { mutableFloatStateOf(40f) }

    // View State
    var bgMode by remember { mutableIntStateOf(0) }
    val bgColors = listOf(Color.DarkGray, Color.Black, Color.White, Color.Red, Color.Green)

    // Transform State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Touch/Magnifier State
    var touchPosition by remember { mutableStateOf(Offset.Unspecified) }
    var showMagnifier by remember { mutableStateOf(false) }

    // Lasso State
    var lassoPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Crop Launcher
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                val stream = context.contentResolver.openInputStream(uri)
                val cropped = android.graphics.BitmapFactory.decodeStream(stream)
                viewModel.loadImage(cropped)
                scale = 1f; offset = Offset.Zero; selectedTool = EditorTool.PAN_ZOOM
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Background Editor", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }
                    IconButton(onClick = { viewModel.redo() }) { Icon(Icons.Default.Redo, "Redo", tint = Color.White) }
                    Button(
                        onClick = { currentBitmap?.let { onSaveClick(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("SAVE", color = Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF1E1E1E)).padding(bottom = 8.dp)) {
                // Sliders
                if (selectedTool == EditorTool.ERASE || selectedTool == EditorTool.RESTORE) {
                    SliderControl("Brush Size", brushSize, 10f..200f) { brushSize = it }
                }
                if (selectedTool == EditorTool.MAGIC) {
                    SliderControl("Tolerance", tolerance, 1f..100f) { tolerance = it }
                }
                if (selectedTool == EditorTool.LASSO) {
                    Text("Draw a loop to cut", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
                }

                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolButton(EditorTool.PAN_ZOOM, selectedTool == EditorTool.PAN_ZOOM) { selectedTool = EditorTool.PAN_ZOOM }
                    ToolButton(EditorTool.CROP, false) {
                        currentBitmap?.let { bmp ->
                            val uri = saveBitmapToCache(context, bmp)
                            cropLauncher.launch(CropImageContractOptions(uri, CropImageOptions()))
                        }
                    }
                    ToolButton(EditorTool.AUTO, selectedTool == EditorTool.AUTO) { viewModel.autoRemoveBackground() }
                    ToolButton(EditorTool.MAGIC, selectedTool == EditorTool.MAGIC) { selectedTool = EditorTool.MAGIC }
                    ToolButton(EditorTool.LASSO, selectedTool == EditorTool.LASSO) { selectedTool = EditorTool.LASSO }
                    ToolButton(EditorTool.ERASE, selectedTool == EditorTool.ERASE) { selectedTool = EditorTool.ERASE }
                    ToolButton(EditorTool.RESTORE, selectedTool == EditorTool.RESTORE) { selectedTool = EditorTool.RESTORE }
                    ToolButton(EditorTool.BG_CHECK, false) { bgMode = (bgMode + 1) % bgColors.size }
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

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        // 1. ZOOM / PAN GESTURES
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (selectedTool == EditorTool.PAN_ZOOM) {
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    offset += pan
                                }
                            }
                        }
                        // 2. MAGIC WAND TAPS
                        .pointerInput(selectedTool, tolerance) {
                            if (selectedTool == EditorTool.MAGIC) {
                                detectTapGestures { tapOffset ->
                                    // FIXED: Using tapOffset and size.width/height directly
                                    val (bmpX, bmpY) = screenToBitmap(
                                        tapOffset,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        offset,
                                        scale,
                                        bitmap.width.toFloat(),
                                        bitmap.height.toFloat()
                                    )
                                    viewModel.magicRemove(bmpX, bmpY, tolerance)
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                        }
                        // 3. DRAG GESTURES (Eraser, Restore, Lasso)
                        .pointerInput(selectedTool, brushSize) {
                            if (selectedTool in listOf(EditorTool.ERASE, EditorTool.RESTORE, EditorTool.LASSO)) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        showMagnifier = true
                                        touchPosition = startOffset
                                        if (selectedTool == EditorTool.LASSO) {
                                            lassoPath = listOf(startOffset)
                                        } else {
                                            viewModel.saveToHistory()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        touchPosition = change.position

                                        if (selectedTool == EditorTool.LASSO) {
                                            lassoPath = lassoPath + change.position
                                        } else {
                                            // Eraser/Restore
                                            // FIXED: Using size.width/height directly
                                            val (bmpX, bmpY) = screenToBitmap(
                                                change.position,
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                offset,
                                                scale,
                                                bitmap.width.toFloat(),
                                                bitmap.height.toFloat()
                                            )
                                            viewModel.applyManualBrush(bmpX.toFloat(), bmpY.toFloat(), brushSize / scale, selectedTool == EditorTool.ERASE)
                                        }
                                    },
                                    onDragEnd = {
                                        showMagnifier = false
                                        touchPosition = Offset.Unspecified
                                        if (selectedTool == EditorTool.LASSO) {
                                            viewModel.applyLasso(lassoPath, scale, offset.x, offset.y, size.width, size.height)
                                            lassoPath = emptyList()
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    val canvasW = size.width
                    val canvasH = size.height

                    // --- DRAW IMAGE ---
                    with(drawContext.canvas.nativeCanvas) {
                        save()
                        translate(canvasW / 2 + offset.x, canvasH / 2 + offset.y)
                        scale(scale, scale)
                        translate(-bitmap.width / 2f, -bitmap.height / 2f)
                        drawBitmap(bitmap, 0f, 0f, null)
                        restore()
                    }

                    // --- DRAW LASSO PATH (Visual Feedback) ---
                    if (selectedTool == EditorTool.LASSO && lassoPath.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(lassoPath.first().x, lassoPath.first().y)
                            lassoPath.forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                    }

                    // --- DRAW MAGNIFIER (The "Loupe") ---
                    if (showMagnifier && touchPosition != Offset.Unspecified) {
                        val magnifierSize = 250f
                        val zoomLevel = 2f
                        val magnifierCenter = touchPosition - Offset(0f, 200f)
                        val magnifierRadius = magnifierSize / 2

                        drawCircle(Color.White, radius = magnifierRadius + 4f, center = magnifierCenter)
                        drawCircle(Color.Black, radius = magnifierRadius, center = magnifierCenter)

                        clipPath(Path().apply { addOval(Rect(center = magnifierCenter, radius = magnifierRadius)) }) {
                            with(drawContext.canvas.nativeCanvas) {
                                save()
                                translate(magnifierCenter.x, magnifierCenter.y)
                                scale(scale * zoomLevel, scale * zoomLevel)

                                val (bmpX, bmpY) = screenToBitmap(
                                    touchPosition,
                                    canvasW,
                                    canvasH,
                                    offset,
                                    scale,
                                    bitmap.width.toFloat(),
                                    bitmap.height.toFloat()
                                )
                                translate(-bmpX.toFloat() + bitmap.width/2f - bitmap.width/2f, -bmpY.toFloat())
                                translate(-bitmap.width/2f, -bitmap.height/2f)

                                drawBitmap(bitmap, 0f, 0f, null)
                                restore()
                            }
                            // Crosshair
                            drawLine(Color.Red, start = magnifierCenter - Offset(20f, 0f), end = magnifierCenter + Offset(20f, 0f), strokeWidth = 2f)
                            drawLine(Color.Red, start = magnifierCenter - Offset(0f, 20f), end = magnifierCenter + Offset(0f, 20f), strokeWidth = 2f)
                        }
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFBB86FC))
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

fun screenToBitmap(touch: Offset, cW: Float, cH: Float, pan: Offset, scale: Float, bmpW: Float, bmpH: Float): Pair<Int, Int> {
    val centeredX = touch.x - (cW / 2 + pan.x)
    val centeredY = touch.y - (cH / 2 + pan.y)
    val unscaledX = centeredX / scale
    val unscaledY = centeredY / scale
    val finalX = unscaledX + bmpW / 2
    val finalY = unscaledY + bmpH / 2
    return Pair(finalX.toInt(), finalY.toInt())
}

@Composable
fun SliderControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFBB86FC), activeTrackColor = Color(0xFFBB86FC))
        )
    }
}

@Composable
fun ToolButton(tool: EditorTool, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color(0xFFBB86FC) else Color.Transparent)
                .border(1.dp, if (isSelected) Color.Transparent else Color.Gray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, tool.label, tint = if (isSelected) Color.Black else Color.White)
        }
        Text(tool.label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): android.net.Uri {
    val file = java.io.File(context.cacheDir, "edit_temp.png")
    java.io.FileOutputStream(file).use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    return android.net.Uri.fromFile(file)
}