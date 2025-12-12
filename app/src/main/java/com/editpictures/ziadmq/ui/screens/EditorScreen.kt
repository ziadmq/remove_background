package com.editpictures.ziadmq.ui.screens

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.editpictures.ziadmq.ui.viewmodel.EditorViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File
import java.io.FileOutputStream

enum class EditorTool(val label: String, val icon: ImageVector) {
    PAN_ZOOM("Zoom", Icons.Default.PanTool),
    CROP("Crop", Icons.Default.Crop),
    AUTO("Auto AI", Icons.Default.AutoFixHigh),
    MAGIC("Magic", Icons.Default.Colorize),
    MAGIC_BRUSH("Magic Eraser", Icons.Default.AutoFixNormal),
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

    // --- ADS STATE ---
    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }

    // Load Interstitial Ad when screen opens
    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()
        // TEST ID for Interstitial: ca-app-pub-3940256099942544/1033173712
        InterstitialAd.load(context, "ca-app-pub-2172903105244124/5382052054", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
            }
        })
    }

    // Function to show Ad then Save
    fun showAdAndSave(bitmap: Bitmap) {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // Save AFTER ad is closed
                    onSaveClick(bitmap)
                    mInterstitialAd = null // Reset
                }
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    onSaveClick(bitmap)
                }
            }
            mInterstitialAd?.show(context as Activity)
        } else {
            // If ad not loaded, save immediately
            onSaveClick(bitmap)
        }
    }

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
    var actionPosition by remember { mutableStateOf(Offset.Unspecified) }
    var showMagnifier by remember { mutableStateOf(false) }

    // Lasso State
    var lassoPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // CONSTANTS FOR OFFSETS
    val TOUCH_OFFSET_Y = 200f // Distance between Finger and Cursor

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
                        onClick = { currentBitmap?.let { showAdAndSave(it) } }, // CHANGED: Calls ad function
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("SAVE", color = Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF1E1E1E)).padding(bottom = 0.dp)) { // Removed padding bottom for ad
                // Sliders
                if (selectedTool in listOf(EditorTool.ERASE, EditorTool.RESTORE, EditorTool.MAGIC_BRUSH)) {
                    SliderControl("Brush Size", brushSize, 10f..200f) { brushSize = it }
                }
                if (selectedTool in listOf(EditorTool.MAGIC, EditorTool.MAGIC_BRUSH)) {
                    SliderControl("Tolerance", tolerance, 1f..100f) { tolerance = it }
                }
                if (selectedTool == EditorTool.LASSO) {
                    Text("Draw a loop to cut", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
                }

                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    ToolButton(EditorTool.MAGIC_BRUSH, selectedTool == EditorTool.MAGIC_BRUSH) { selectedTool = EditorTool.MAGIC_BRUSH }
                    ToolButton(EditorTool.LASSO, selectedTool == EditorTool.LASSO) { selectedTool = EditorTool.LASSO }
                    ToolButton(EditorTool.ERASE, selectedTool == EditorTool.ERASE) { selectedTool = EditorTool.ERASE }
                    ToolButton(EditorTool.RESTORE, selectedTool == EditorTool.RESTORE) { selectedTool = EditorTool.RESTORE }
                    ToolButton(EditorTool.BG_CHECK, false) { bgMode = (bgMode + 1) % bgColors.size }
                }

                // --- BANNER AD AT BOTTOM ---
                BannerAdView()
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
                        // 2. DRAG GESTURES (Merged Tools)
                        .pointerInput(selectedTool, brushSize, tolerance) {
                            if (selectedTool in listOf(EditorTool.ERASE, EditorTool.RESTORE, EditorTool.LASSO, EditorTool.MAGIC_BRUSH, EditorTool.MAGIC)) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        showMagnifier = true
                                        touchPosition = startOffset
                                        val realChangePoint = startOffset - Offset(0f, TOUCH_OFFSET_Y)
                                        actionPosition = realChangePoint

                                        if (selectedTool == EditorTool.LASSO) {
                                            lassoPath = listOf(realChangePoint)
                                        } else {
                                            viewModel.saveToHistory()
                                            if (selectedTool == EditorTool.MAGIC_BRUSH) {
                                                val (bmpX, bmpY) = screenToBitmap(realChangePoint, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                                viewModel.initMagicBrush(bmpX.toFloat(), bmpY.toFloat())
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        touchPosition = change.position
                                        val realChangePoint = change.position - Offset(0f, TOUCH_OFFSET_Y)
                                        actionPosition = realChangePoint

                                        if (selectedTool == EditorTool.LASSO) {
                                            lassoPath = lassoPath + realChangePoint
                                        } else {
                                            val (bmpX, bmpY) = screenToBitmap(realChangePoint, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())

                                            if (selectedTool == EditorTool.MAGIC_BRUSH) {
                                                viewModel.applyMagicBrush(bmpX.toFloat(), bmpY.toFloat(), brushSize / scale, tolerance)
                                            } else if (selectedTool == EditorTool.ERASE) {
                                                viewModel.applyManualBrush(bmpX.toFloat(), bmpY.toFloat(), brushSize / scale, true)
                                            } else if (selectedTool == EditorTool.RESTORE) {
                                                viewModel.applyManualBrush(bmpX.toFloat(), bmpY.toFloat(), brushSize / scale, false)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (selectedTool == EditorTool.LASSO) {
                                            viewModel.applyLasso(lassoPath, scale, offset.x, offset.y, size.width, size.height)
                                            lassoPath = emptyList()
                                        } else if (selectedTool == EditorTool.MAGIC && actionPosition != Offset.Unspecified) {
                                            val (bmpX, bmpY) = screenToBitmap(actionPosition, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                            viewModel.magicRemove(bmpX, bmpY, tolerance)
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        showMagnifier = false
                                        touchPosition = Offset.Unspecified
                                        actionPosition = Offset.Unspecified
                                    }
                                )
                            }
                        }
                ) {
                    val canvasW = size.width
                    val canvasH = size.height
                    with(drawContext.canvas.nativeCanvas) {
                        save()
                        translate(canvasW / 2 + offset.x, canvasH / 2 + offset.y)
                        scale(scale, scale)
                        translate(-bitmap.width / 2f, -bitmap.height / 2f)
                        drawBitmap(bitmap, 0f, 0f, null)
                        restore()
                    }
                    if (selectedTool != EditorTool.PAN_ZOOM && actionPosition != Offset.Unspecified) {
                        val radius = if (selectedTool == EditorTool.MAGIC) 10f else (brushSize / 2 * scale)
                        drawCircle(Color.White, radius = radius, center = actionPosition, style = Stroke(width = 2f))
                        drawCircle(Color.Red, radius = 4f, center = actionPosition)
                    }
                    if (selectedTool == EditorTool.LASSO && lassoPath.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(lassoPath.first().x, lassoPath.first().y)
                            lassoPath.forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                    }
                    if (showMagnifier && actionPosition != Offset.Unspecified) {
                        val magSize = 300f
                        val zoomLevel = 2.5f
                        val boxLeft = (canvasW - magSize) / 2
                        val boxTop = 50f
                        val magRect = Rect(boxLeft, boxTop, boxLeft + magSize, boxTop + magSize)
                        drawRoundRect(color = Color(0xFF333333), topLeft = Offset(boxLeft, boxTop), size = Size(magSize, magSize), cornerRadius = CornerRadius(20f, 20f))
                        clipPath(Path().apply { addRoundRect(RoundRect(magRect, CornerRadius(20f, 20f))) }) {
                            drawRect(Color.White, topLeft = Offset(boxLeft, boxTop), size = Size(magSize, magSize))
                            with(drawContext.canvas.nativeCanvas) {
                                save()
                                translate(magRect.center.x, magRect.center.y)
                                scale(scale * zoomLevel, scale * zoomLevel)
                                val (bmpX, bmpY) = screenToBitmap(actionPosition, canvasW, canvasH, offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                translate(-bmpX.toFloat(), -bmpY.toFloat())
                                drawBitmap(bitmap, 0f, 0f, null)
                                restore()
                            }
                            val c = magRect.center
                            drawLine(Color.Red, start = c - Offset(30f, 0f), end = c + Offset(30f, 0f), strokeWidth = 3f)
                            drawLine(Color.Red, start = c - Offset(0f, 30f), end = c + Offset(0f, 30f), strokeWidth = 3f)
                        }
                        drawRoundRect(color = Color.White, topLeft = Offset(boxLeft, boxTop), size = Size(magSize, magSize), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 4f))
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFBB86FC))
            }
        }
    }
}

// --- COMPOSABLE FOR BANNER AD ---
@Composable
fun BannerAdView() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                // TEST ID for Banner: ca-app-pub-3940256099942544/6300978111
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-2172903105244124/3397858876"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
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