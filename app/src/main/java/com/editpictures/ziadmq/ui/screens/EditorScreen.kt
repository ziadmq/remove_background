package com.editpictures.ziadmq.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.*
import com.editpictures.ziadmq.ui.viewmodel.BackgroundType
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*
import androidx.compose.ui.graphics.toArgb
import com.editpictures.ziadmq.ui.component.BannerAdView
import com.editpictures.ziadmq.ui.component.ToolButton
import com.editpictures.ziadmq.ui.viewmodel.EditorViewModel

enum class EditorTool(val label: String, val icon: ImageVector) {
    PAN_ZOOM("Zoom", Icons.Default.PanTool),
    CROP("Crop", Icons.Default.Crop),
    AUTO("Auto AI", Icons.Default.AutoFixHigh),
    MAGIC("Magic", Icons.Default.Colorize),
    MAGIC_BRUSH("Magic Eraser", Icons.Default.AutoFixNormal),
    ERASE("Erase", Icons.Default.CleaningServices),
    RESTORE("Restore", Icons.Default.Brush),
    LASSO("Lasso", Icons.Default.Gesture),
    BACKGROUND("BG", Icons.Default.Image)
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
    val backgroundType by viewModel.backgroundType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    // نظام الإعلانات
    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    LaunchedEffect(Unit) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, "ca-app-pub-2172903105244124/5382052054", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) { mInterstitialAd = interstitialAd }
        })
    }

    fun showAdAndSave() {
        val bitmapToSave = viewModel.getCompositedBitmap() ?: return
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { onSaveClick(bitmapToSave); mInterstitialAd = null }
                override fun onAdFailedToShowFullScreenContent(p0: AdError) { onSaveClick(bitmapToSave) }
            }
            mInterstitialAd?.show(context as Activity)
        } else { onSaveClick(bitmapToSave) }
    }

    var selectedTool by remember { mutableStateOf(EditorTool.PAN_ZOOM) }
    var brushSize by remember { mutableFloatStateOf(60f) }
    var tolerance by remember { mutableFloatStateOf(40f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var actionPosition by remember { mutableStateOf(Offset.Unspecified) }
    var showMagnifier by remember { mutableStateOf(false) }
    var lassoPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val TOUCH_OFFSET_Y = 200f

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            viewModel.setBackground(BackgroundType.Image(bmp))
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                val stream = context.contentResolver.openInputStream(uri)
                val cropped = BitmapFactory.decodeStream(stream)
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
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }
                    IconButton(onClick = { viewModel.redo() }) { Icon(Icons.Default.Redo, "Redo", tint = Color.White) }
                    Button(
                        onClick = { showAdAndSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("SAVE", color = Color.Black) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF1E1E1E))) {
                if (selectedTool == EditorTool.BACKGROUND) {
                    BackgroundLibraryRow(
                        onColorSelect = { viewModel.setBackground(BackgroundType.Color(it.toArgb())) },
                        onGradientSelect = { viewModel.setBackground(BackgroundType.Gradient(it.map { c -> c.toArgb() }.toIntArray())) },
                        onCustomImageClick = { bgImagePicker.launch("image/*") },
                        onTransparentClick = { viewModel.setBackground(BackgroundType.Transparent) }
                    )
                } else {
                    if (selectedTool in listOf(EditorTool.ERASE, EditorTool.RESTORE, EditorTool.MAGIC_BRUSH)) {
                        SliderControl("Brush Size", brushSize, 10f..200f) { brushSize = it }
                    }
                    if (selectedTool in listOf(EditorTool.MAGIC, EditorTool.MAGIC_BRUSH)) {
                        SliderControl("Tolerance", tolerance, 1f..100f) { tolerance = it }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolButton(EditorTool.PAN_ZOOM, selectedTool == EditorTool.PAN_ZOOM) { selectedTool = EditorTool.PAN_ZOOM }
                    ToolButton(EditorTool.CROP, false) {
                        currentBitmap?.let { bmp ->
                            val uri = saveBitmapToCache(context, bmp)
                            cropLauncher.launch(CropImageContractOptions(uri, CropImageOptions()))
                        }
                    }
                    ToolButton(EditorTool.AUTO, false) { viewModel.autoRemoveBackground() }
                    ToolButton(EditorTool.BACKGROUND, selectedTool == EditorTool.BACKGROUND) { selectedTool = EditorTool.BACKGROUND }
                    ToolButton(EditorTool.MAGIC, selectedTool == EditorTool.MAGIC) { selectedTool = EditorTool.MAGIC }
                    ToolButton(EditorTool.MAGIC_BRUSH, selectedTool == EditorTool.MAGIC_BRUSH) { selectedTool = EditorTool.MAGIC_BRUSH }
                    ToolButton(EditorTool.LASSO, selectedTool == EditorTool.LASSO) { selectedTool = EditorTool.LASSO }
                    ToolButton(EditorTool.ERASE, selectedTool == EditorTool.ERASE) { selectedTool = EditorTool.ERASE }
                    ToolButton(EditorTool.RESTORE, selectedTool == EditorTool.RESTORE) { selectedTool = EditorTool.RESTORE }
                }
                BannerAdView()
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).clip(RectangleShape)
        ) {
            // رسم الخلفية المختارة تحت الصورة
            Box(modifier = Modifier.fillMaxSize()) {
                when (val bg = backgroundType) {
                    is BackgroundType.Color -> Box(Modifier.fillMaxSize().background(Color(bg.color)))
                    is BackgroundType.Gradient -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(bg.colors.map { Color(it) })))
                    is BackgroundType.Image -> Image(bitmap = bg.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = 20.dp.toPx()
                        for (x in 0 until (size.width / cellSize).toInt() + 1) {
                            for (y in 0 until (size.height / cellSize).toInt() + 1) {
                                if ((x + y) % 2 == 0) drawRect(Color.LightGray, Offset(x * cellSize, y * cellSize), Size(cellSize, cellSize))
                            }
                        }
                    }
                }
            }

            if (currentBitmap != null) {
                val bitmap = currentBitmap!!
                Canvas(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (selectedTool == EditorTool.PAN_ZOOM) {
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    offset += pan
                                }
                            }
                        }
                        .pointerInput(selectedTool, brushSize, tolerance) {
                            if (selectedTool != EditorTool.PAN_ZOOM && selectedTool != EditorTool.BACKGROUND) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        showMagnifier = true
                                        val realPoint = startOffset - Offset(0f, TOUCH_OFFSET_Y)
                                        actionPosition = realPoint
                                        if (selectedTool == EditorTool.LASSO) lassoPath = listOf(realPoint)
                                        else {
                                            viewModel.saveToHistory()
                                            if (selectedTool == EditorTool.MAGIC_BRUSH) {
                                                val (bx, by) = screenToBitmap(realPoint, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                                viewModel.initMagicBrush(bx.toFloat(), by.toFloat())
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val realPoint = change.position - Offset(0f, TOUCH_OFFSET_Y)
                                        actionPosition = realPoint
                                        if (selectedTool == EditorTool.LASSO) lassoPath = lassoPath + realPoint
                                        else {
                                            val (bx, by) = screenToBitmap(realPoint, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                            when (selectedTool) {
                                                EditorTool.MAGIC_BRUSH -> viewModel.applyMagicBrush(bx.toFloat(), by.toFloat(), brushSize / scale, tolerance)
                                                EditorTool.ERASE -> viewModel.applyManualBrush(bx.toFloat(), by.toFloat(), brushSize / scale, true)
                                                EditorTool.RESTORE -> viewModel.applyManualBrush(bx.toFloat(), by.toFloat(), brushSize / scale, false)
                                                else -> {}
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (selectedTool == EditorTool.LASSO) {
                                            viewModel.applyLasso(lassoPath, scale, offset.x, offset.y, size.width, size.height)
                                            lassoPath = emptyList()
                                        } else if (selectedTool == EditorTool.MAGIC && actionPosition != Offset.Unspecified) {
                                            val (bx, by) = screenToBitmap(actionPosition, size.width.toFloat(), size.height.toFloat(), offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                            viewModel.magicRemove(bx, by, tolerance)
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        showMagnifier = false
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
                        val r = if (selectedTool == EditorTool.MAGIC) 10f else (brushSize / 2 * scale)
                        drawCircle(Color.White, radius = r, center = actionPosition, style = Stroke(width = 2f))
                        drawCircle(Color.Red, radius = 4f, center = actionPosition)
                    }

                    if (selectedTool == EditorTool.LASSO && lassoPath.isNotEmpty()) {
                        val p = Path().apply {
                            moveTo(lassoPath.first().x, lassoPath.first().y)
                            lassoPath.forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(p, color = Color.Red, style = Stroke(width = 5f))
                    }

                    if (showMagnifier && actionPosition != Offset.Unspecified) {
                        val mSize = 300f
                        val zLevel = 2.5f
                        val bLeft = (canvasW - mSize) / 2
                        val bTop = 50f
                        val mRect = Rect(bLeft, bTop, bLeft + mSize, bTop + mSize)

                        drawRoundRect(Color(0xFF333333), Offset(bLeft, bTop), Size(mSize, mSize), CornerRadius(20f, 20f))

                        clipPath(Path().apply { addRoundRect(RoundRect(mRect, CornerRadius(20f, 20f))) }) {
                            drawRect(Color.White, Offset(bLeft, bTop), Size(mSize, mSize))
                            with(drawContext.canvas.nativeCanvas) {
                                save()
                                translate(mRect.center.x, mRect.center.y)
                                scale(scale * zLevel, scale * zLevel)
                                val (bx, by) = screenToBitmap(actionPosition, canvasW, canvasH, offset, scale, bitmap.width.toFloat(), bitmap.height.toFloat())
                                translate(-bx.toFloat(), -by.toFloat())
                                drawBitmap(bitmap, 0f, 0f, null)
                                restore()
                            }
                            val c = mRect.center
                            drawLine(Color.Red, c - Offset(30f, 0f), c + Offset(30f, 0f), 3f)
                            drawLine(Color.Red, c - Offset(0f, 30f), c + Offset(0f, 30f), 3f)
                        }
                        drawRoundRect(Color.White, Offset(bLeft, bTop), Size(mSize, mSize), CornerRadius(20f, 20f), Stroke(4f))
                    }
                }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFBB86FC))
        }
    }
}

@Composable
fun BackgroundLibraryRow(
    onColorSelect: (Color) -> Unit,
    onGradientSelect: (List<Color>) -> Unit,
    onCustomImageClick: () -> Unit,
    onTransparentClick: () -> Unit
) {
    val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
    val gradients = listOf(
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
        listOf(Color(0xFFF09819), Color(0xFFEDDE5D)),
        listOf(Color(0xFF11998e), Color(0xFF38ef7d))
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(50.dp).border(1.dp, Color.Gray, CircleShape).clickable { onTransparentClick() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Clear, null, tint = Color.White)
        }
        Box(modifier = Modifier.size(50.dp).background(Color.DarkGray, CircleShape).clickable { onCustomImageClick() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.White)
        }
        colors.forEach { color ->
            Box(modifier = Modifier.size(50.dp).background(color, CircleShape).border(1.dp, Color.LightGray, CircleShape).clickable { onColorSelect(color) })
        }
        gradients.forEach { grad ->
            Box(modifier = Modifier.size(50.dp).background(Brush.linearGradient(grad), CircleShape).clickable { onGradientSelect(grad) })
        }
    }
}

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

fun saveBitmapToCache(context: android.content.Context, bitmap: Bitmap): android.net.Uri {
    val file = java.io.File(context.cacheDir, "edit_temp.png")
    java.io.FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return android.net.Uri.fromFile(file)
}