package com.gatha.gpscamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private val Ink = Color(0xFF101113)
private val Panel = Color(0xFF1B1D20)
private val Accent = Color(0xFFE8F0FF)
private val Muted = Color(0xFFB6BAC3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { GpsCameraApp() }
    }
}

data class GpsStamp(
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val capturedAt: Date,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?
)

data class StampSettings(
    val showCoordinates: Boolean = true,
    val showAddress: Boolean = true,
    val showDateTime: Boolean = true,
    val showAccuracy: Boolean = true,
    val showAltitude: Boolean = true,
    val position: String = "Bottom Right",
    val textScale: Float = 1f,
    val opacity: Float = 0.75f,
    val style: String = "Classic"
)

@Composable
private fun GpsCameraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var locationPermission by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking location…") }
    var lastSaved by remember { mutableStateOf<Uri?>(null) }
    var lastLocation by remember { mutableStateOf<GpsStamp?>(null) }
    var showStampSettings by remember { mutableStateOf(false) }
    var stampSettings by remember { mutableStateOf(loadStampSettings(context)) }
    var aspectRatio by remember { mutableStateOf(loadAspectRatio(context)) }
    var ratioMenuExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraPermission = result[Manifest.permission.CAMERA] == true || hasPermission(context, Manifest.permission.CAMERA)
        locationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        status = if (locationPermission) "Location permission available" else "Location permission not granted"
        if (!locationPermission || !hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(context, "Allow precise location for the best GPS stamp accuracy.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val requested = buildList {
            if (!cameraPermission) add(Manifest.permission.CAMERA)
            if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (requested.isNotEmpty()) permissionLauncher.launch(requested.toTypedArray())
    }

    if (showStampSettings) {
        StampSettingsPage(
            settings = stampSettings,
            locationStatus = status,
            locationStamp = lastLocation,
            flashMode = flashMode,
            onFlashModeChange = { flashMode = it },
            aspectRatio = aspectRatio,
            onAspectRatioChange = { aspectRatio = it; saveAspectRatio(context, it) },
            onSettingsChange = { updated -> stampSettings = updated; saveStampSettings(context, updated) },
            onBack = { showStampSettings = false }
        )
    } else {
        val fullScreenMode = aspectRatio == "Full screen"
        val activity = context as? ComponentActivity
        DisposableEffect(fullScreenMode, showStampSettings) {
            val window = activity?.window
            val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, it.decorView) }
            if (fullScreenMode && !showStampSettings) {
                controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                if (!fullScreenMode) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color.Black)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            Surface(
                                onClick = { ratioMenuExpanded = true },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF242424),
                                contentColor = Color.White,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(aspectRatio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("▾", fontSize = 12.sp)
                                }
                            }
                            DropdownMenu(expanded = ratioMenuExpanded, onDismissRequest = { ratioMenuExpanded = false }) {
                                listOf("1:1", "9:16", "3:4", "Full screen").forEach { ratio ->
                                    DropdownMenuItem(
                                        text = { Text(if (ratio == aspectRatio) "✓  $ratio" else ratio) },
                                        onClick = { aspectRatio = ratio; saveAspectRatio(context, ratio); ratioMenuExpanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            CameraRoundButton(onClick = {
                                val photoUri = lastSaved ?: findLatestStampedPhoto(context)
                                if (photoUri == null) Toast.makeText(context, "No GPS Camera photos yet", Toast.LENGTH_SHORT).show()
                                else openInNativePhotoViewer(context, photoUri)
                            }, description = "Open gallery", icon = { Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(25.dp)) })
                            ShutterButton(cameraPermission, busy, imageCapture, context, scope, stampSettings, aspectRatio,
                                onBusyChange = { busy = it }, onStatusChange = { status = it }, onLocationChange = { lastLocation = it }, onSaved = { lastSaved = it })
                            CameraRoundButton(enabled = !busy, onClick = { useFrontCamera = !useFrontCamera }, description = "Flip camera", icon = {
                                Icon(Icons.Default.FlipCameraAndroid, null, tint = if (busy) Muted else Color.White, modifier = Modifier.size(25.dp))
                            })
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().then(if (fullScreenMode) Modifier else Modifier.padding(padding)).background(Color.Black)) {
                if (cameraPermission) {
                    CameraPreview(
                        useFrontCamera = useFrontCamera,
                        flashMode = flashMode,
                        onCameraReady = { camera, capture -> cameraControl = camera; imageCapture = capture },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (aspectRatio != "Full screen") {
                        val targetRatio = when (aspectRatio) {
                            "1:1" -> 1f
                            "9:16" -> 9f / 16f
                            else -> 3f / 4f
                        }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val availableRatio = maxWidth.value / maxHeight.value
                            if (availableRatio > targetRatio) {
                                val frameWidth = (maxHeight.value * targetRatio).dp.coerceAtMost(maxWidth)
                                val sideBand = ((maxWidth - frameWidth) / 2).coerceAtLeast(0.dp)
                                Row(Modifier.fillMaxSize()) {
                                    Box(Modifier.width(sideBand).fillMaxHeight().background(Color.Black.copy(alpha = 0.72f)))
                                    Spacer(Modifier.weight(1f))
                                    Box(Modifier.width(sideBand).fillMaxHeight().background(Color.Black.copy(alpha = 0.72f)))
                                }
                            } else {
                                val frameHeight = (maxWidth.value / targetRatio).dp.coerceAtMost(maxHeight)
                                val band = ((maxHeight - frameHeight) / 2).coerceAtLeast(0.dp)
                                Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxWidth().height(band).background(Color.Black.copy(alpha = 0.72f)))
                                    Spacer(Modifier.weight(1f))
                                    Box(Modifier.fillMaxWidth().height(band).background(Color.Black.copy(alpha = 0.72f)))
                                }
                            }
                        }
                    }
                    // Settings is always in the upper-right corner, safely inset from the display edge.
                    CameraRoundButton(
                        onClick = { showStampSettings = true }, description = "Camera settings",
                        icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(24.dp)) },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .then(if (fullScreenMode) Modifier.padding(top = 22.dp, end = 22.dp) else Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 14.dp, end = 22.dp))
                    )
                    if (fullScreenMode) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .then(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                                .padding(start = 28.dp, end = 28.dp, bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                Surface(onClick = { ratioMenuExpanded = true }, shape = RoundedCornerShape(18.dp), color = Color(0xCC242424), contentColor = Color.White, modifier = Modifier.height(34.dp)) {
                                    Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(aspectRatio, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("▾", fontSize = 12.sp)
                                    }
                                }
                                DropdownMenu(expanded = ratioMenuExpanded, onDismissRequest = { ratioMenuExpanded = false }) {
                                    listOf("1:1", "9:16", "3:4", "Full screen").forEach { ratio ->
                                        DropdownMenuItem(text = { Text(if (ratio == aspectRatio) "✓  $ratio" else ratio) }, onClick = { aspectRatio = ratio; saveAspectRatio(context, ratio); ratioMenuExpanded = false })
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                CameraRoundButton(onClick = {
                                    val photoUri = lastSaved ?: findLatestStampedPhoto(context)
                                    if (photoUri == null) Toast.makeText(context, "No GPS Camera photos yet", Toast.LENGTH_SHORT).show()
                                    else openInNativePhotoViewer(context, photoUri)
                                }, description = "Open gallery", icon = { Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(25.dp)) })
                                ShutterButton(cameraPermission, busy, imageCapture, context, scope, stampSettings, aspectRatio,
                                    onBusyChange = { busy = it }, onStatusChange = { status = it }, onLocationChange = { lastLocation = it }, onSaved = { lastSaved = it })
                                CameraRoundButton(enabled = !busy, onClick = { useFrontCamera = !useFrontCamera }, description = "Flip camera", icon = { Icon(Icons.Default.FlipCameraAndroid, null, tint = if (busy) Muted else Color.White, modifier = Modifier.size(25.dp)) })
                            }
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Camera permission required", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text("Allow camera") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    cameraPermission: Boolean,
    busy: Boolean,
    imageCapture: ImageCapture?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    stampSettings: StampSettings,
    aspectRatio: String,
    onBusyChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onLocationChange: (GpsStamp) -> Unit,
    onSaved: (Uri) -> Unit
) {
    Box(Modifier.size(74.dp).clip(CircleShape).background(Color.White).padding(5.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            IconButton(enabled = cameraPermission && !busy, onClick = {
                val capture = imageCapture
                if (capture == null) {
                    Toast.makeText(context, "Camera is starting. Try again.", Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                onBusyChange(true)
                onStatusChange("Capturing photo…")
                scope.launch {
                    try {
                        // Start GPS work and camera capture together. The shutter no longer waits
                        // for a fresh GPS fix or reverse geocoding before the sensor takes the photo.
                        val stampTask = scope.async(Dispatchers.IO) { acquireStamp(context) }
                        val (stampedUri, stamp) = captureAndStamp(context, capture, stampTask, stampSettings, aspectRatio)
                        onLocationChange(stamp)
                        onSaved(stampedUri)
                        onStatusChange(if (stamp.latitude != null && stamp.longitude != null) "Stamped photo saved · GPS ready" else "Stamped photo saved · GPS unavailable")
                        Toast.makeText(context, if (stamp.latitude != null && stamp.longitude != null) "Stamped photo saved" else "Photo saved; GPS details unavailable", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        onStatusChange("Capture failed · ${e.localizedMessage ?: "Please try again"}")
                        Toast.makeText(context, e.localizedMessage ?: "Could not save photo", Toast.LENGTH_LONG).show()
                    } finally { onBusyChange(false) }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                else Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(Color.Black))
            }
        }
    }
}

@Composable
private fun CameraRoundButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(50.dp).clip(CircleShape).background(Color(0xFF242424))
    ) { Box(contentAlignment = Alignment.Center) { icon() } }
}

private fun loadAspectRatio(context: Context): String {
    val prefs = context.getSharedPreferences("gps_camera_preferences", Context.MODE_PRIVATE)
    // One-time migration: earlier builds defaulted to Full screen. Start this UI update at 3:4,
    // then preserve any ratio the user selects from this version onward.
    if (!prefs.getBoolean("ratio_default_3_4_migrated", false)) {
        prefs.edit().putString("aspectRatio", "3:4").putBoolean("ratio_default_3_4_migrated", true).apply()
        return "3:4"
    }
    return prefs.getString("aspectRatio", "3:4")?.takeIf { it in listOf("1:1", "9:16", "3:4", "Full screen") } ?: "3:4"
}

private fun saveAspectRatio(context: Context, ratio: String) {
    context.getSharedPreferences("gps_camera_preferences", Context.MODE_PRIVATE).edit().putString("aspectRatio", ratio).apply()
}

@Composable
private fun StampSettingsPage(
    settings: StampSettings,
    locationStatus: String,
    locationStamp: GpsStamp?,
    flashMode: Int,
    onFlashModeChange: (Int) -> Unit,
    aspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    onSettingsChange: (StampSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    var showLocationDetails by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Ink,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(Panel)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to camera", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    Text("Make every location photo yours", color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF25364B)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFBBD7FF), modifier = Modifier.size(22.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(Ink)
                .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "CAMERA", subtitle = "Quick controls are kept off the viewfinder to keep capture clean.") {
                Text("Photo ratio", color = Color.White, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1:1", "9:16", "3:4", "Full screen").forEach { ratio ->
                        FilterChip(modifier = Modifier.weight(1f), selected = aspectRatio == ratio,
                            onClick = { onAspectRatioChange(ratio) }, label = { Text(ratio, maxLines = 1, fontSize = 11.sp) })
                    }
                }
                Text("Flash", color = Color.White, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto" to ImageCapture.FLASH_MODE_AUTO, "On" to ImageCapture.FLASH_MODE_ON, "Off" to ImageCapture.FLASH_MODE_OFF).forEach { (label, value) ->
                        FilterChip(modifier = Modifier.weight(1f), selected = flashMode == value,
                            onClick = { onFlashModeChange(value) }, label = { Text(label) })
                    }
                }
                Text("The ratio selector is also available directly above the shutter. The selected ratio is applied to the saved photo.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            SettingsSection(title = "PHOTO STAMP", subtitle = "Choose which details are printed on every new photo.") {
                StampToggle("Coordinates", settings.showCoordinates) { onSettingsChange(settings.copy(showCoordinates = it)) }
                StampToggle("Address / locality", settings.showAddress) { onSettingsChange(settings.copy(showAddress = it)) }
                StampToggle("Date and time", settings.showDateTime) { onSettingsChange(settings.copy(showDateTime = it)) }
                StampToggle("GPS accuracy", settings.showAccuracy) { onSettingsChange(settings.copy(showAccuracy = it)) }
                StampToggle("Altitude", settings.showAltitude) { onSettingsChange(settings.copy(showAltitude = it)) }
            }
            SettingsSection(title = "STAMP APPEARANCE", subtitle = "Fine-tune the stamp layout and readability.") {
                Text("Layout", color = Color.White, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Classic", "Compact", "Elegant").forEach { style ->
                        FilterChip(
                            modifier = Modifier.weight(1f), selected = settings.style == style,
                            onClick = { onSettingsChange(settings.copy(style = style)) },
                            label = { Text(style, maxLines = 1) }
                        )
                    }
                }
                Text("Stamp position", color = Color.White, fontWeight = FontWeight.Medium)
                Text("Choose a corner or centre position on the photo.", color = Muted, fontSize = 12.sp)
                val positions = listOf("Top Left", "Top Middle", "Top Right", "Bottom Left", "Bottom Middle", "Bottom Right")
                positions.chunked(3).forEach { rowPositions ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowPositions.forEach { position ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = settings.position == position,
                                onClick = { onSettingsChange(settings.copy(position = position)) },
                                label = { Text(position.replace(" ", "\n"), maxLines = 2, fontSize = 10.sp, lineHeight = 12.sp) }
                            )
                        }
                    }
                }
                Text("Text size · ${(settings.textScale * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(value = settings.textScale, onValueChange = { onSettingsChange(settings.copy(textScale = it)) }, valueRange = 0.75f..1.35f)
                Text("Background opacity · ${(settings.opacity * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(value = settings.opacity, onValueChange = { onSettingsChange(settings.copy(opacity = it)) }, valueRange = 0.35f..0.95f)
                Text("Changes are saved automatically and apply to the next captured photo. The camera preview reflects your stamp choices.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            SettingsSection(title = "LOCATION", subtitle = "GPS information is included only when the device provides it.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(locationStatus, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                Text("If a permission was denied or Android no longer shows its prompt, open the relevant system settings below.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                OutlinedButton(
                    onClick = { openAppPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Open app permissions") }
                OutlinedButton(
                    onClick = { openLocationSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Open phone location settings") }
                OutlinedButton(onClick = { showLocationDetails = !showLocationDetails }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showLocationDetails) "Hide location details" else "Show location details")
                }
                if (showLocationDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(locationStamp?.latitude?.let { "Latitude: %.6f".format(Locale.US, it) } ?: "Latitude: unavailable", color = Muted, fontSize = 12.sp)
                        Text(locationStamp?.longitude?.let { "Longitude: %.6f".format(Locale.US, it) } ?: "Longitude: unavailable", color = Muted, fontSize = 12.sp)
                        Text(locationStamp?.accuracyMeters?.let { "Accuracy: ±%.0f m".format(Locale.US, it) } ?: "Accuracy: unavailable", color = Muted, fontSize = 12.sp)
                        Text(locationStamp?.altitudeMeters?.let { "Altitude: %.1f m".format(Locale.US, it) } ?: "Altitude: unavailable", color = Muted, fontSize = 12.sp)
                        Text(locationStamp?.address ?: "Address: unavailable until a location is captured", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            var updateMessage by remember { mutableStateOf("Check GitHub for the latest GPS Camera release.") }
            var updateBusy by remember { mutableStateOf(false) }
            var releaseUrl by remember { mutableStateOf<String?>(null) }
            SettingsSection(title = "APP UPDATES", subtitle = "Check for a newer version published by Gatha Media on GitHub.") {
                Text(updateMessage, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Button(
                    onClick = {
                        if (!updateBusy) {
                            updateBusy = true
                            updateMessage = "Checking for updates…"
                            updateScope.launch {
                                try {
                                    val result = GitHubUpdateChecker.check(context)
                                    releaseUrl = result.releaseUrl
                                    updateMessage = if (result.hasUpdate) {
                                        "Update available: ${result.latestVersion}. Open the release page to download it. Android will ask you to confirm installation."
                                    } else {
                                        "You are using version ${result.currentVersion}, the latest published version."
                                    }
                                    if (result.hasUpdate) GitHubUpdateChecker.openRelease(context, result.releaseUrl)
                                } catch (e: Exception) {
                                    updateMessage = e.message ?: "Could not check for updates. Please try again later."
                                } finally { updateBusy = false }
                            }
                        }
                    },
                    enabled = !updateBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBD7FF), contentColor = Color(0xFF101113))
                ) { Text(if (updateBusy) "Checking…" else "Check for updates", fontWeight = FontWeight.SemiBold) }
                if (releaseUrl != null) {
                    OutlinedButton(onClick = { GitHubUpdateChecker.openRelease(context, releaseUrl!!) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open GitHub Releases")
                    }
                }
                Text("Updates are downloaded from GitHub Releases. Android requires your approval to install an APK; updates cannot be silently installed.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            SettingsSection(title = "ABOUT GPS CAMERA", subtitle = "Simple, privacy-conscious GPS photo stamping.") {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF25364B)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF344B67)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color(0xFFD5E6FF), modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("GPS Camera", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("BY GATHA MEDIA", color = Color(0xFFBBD7FF), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.3.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Capture photos with location details such as coordinates, address, capture date and time, GPS accuracy, and altitude when available.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        Text("GPS-stamped photos are saved in Pictures/GPS Camera/Stamped and can be opened in your phone gallery.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        Text("Your stamp preferences are stored on this device. GPS Camera does not require an account. Location data is used to prepare the stamp for the photo you capture.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Text("Version 1.9.0", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Made with care by Gatha Media", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            OutlinedButton(
                onClick = { onSettingsChange(StampSettings()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Reset stamp settings", fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 2.dp).width(4.dp).height(34.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF8FB8FF)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        content()
    }
}

@Composable
private fun StampToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun loadStampSettings(context: Context): StampSettings {
    val prefs = context.getSharedPreferences("gps_camera_preferences", Context.MODE_PRIVATE)
    return StampSettings(
        showCoordinates = prefs.getBoolean("showCoordinates", true),
        showAddress = prefs.getBoolean("showAddress", true),
        showDateTime = prefs.getBoolean("showDateTime", true),
        showAccuracy = prefs.getBoolean("showAccuracy", true),
        showAltitude = prefs.getBoolean("showAltitude", true),
        position = prefs.getString("position", "Bottom Right")?.takeIf { it in listOf("Top Left", "Top Middle", "Top Right", "Bottom Left", "Bottom Middle", "Bottom Right") } ?: "Bottom Right",
        textScale = prefs.getFloat("textScale", 1f).coerceIn(0.75f, 1.35f),
        opacity = prefs.getFloat("opacity", 0.75f).coerceIn(0.35f, 0.95f),
        style = prefs.getString("style", "Classic")?.takeIf { it in listOf("Classic", "Compact", "Elegant") } ?: "Classic"
    )
}

private fun saveStampSettings(context: Context, settings: StampSettings) {
    context.getSharedPreferences("gps_camera_preferences", Context.MODE_PRIVATE).edit()
        .putBoolean("showCoordinates", settings.showCoordinates)
        .putBoolean("showAddress", settings.showAddress)
        .putBoolean("showDateTime", settings.showDateTime)
        .putBoolean("showAccuracy", settings.showAccuracy)
        .putBoolean("showAltitude", settings.showAltitude)
        .putString("position", settings.position)
        .putFloat("textScale", settings.textScale.coerceIn(0.75f, 1.35f))
        .putFloat("opacity", settings.opacity.coerceIn(0.35f, 0.95f))
        .putString("style", settings.style)
        .apply()
}

@Composable
private fun CameraPreview(
    useFrontCamera: Boolean,
    flashMode: Int,
    onCameraReady: (Camera, ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var currentCapture by remember { mutableStateOf<ImageCapture?>(null) }
    AndroidView(modifier = modifier, factory = { previewView })

    // Apply flash changes directly to the active capture use case; avoid restarting the camera.
    LaunchedEffect(flashMode, currentCapture) {
        currentCapture?.flashMode = flashMode
    }

    // Bind only when the camera/lifecycle changes, not on every Compose recomposition.
    DisposableEffect(previewView, lifecycleOwner, useFrontCamera) {
        var boundProvider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(100)
                    .setFlashMode(flashMode)
                    .build()
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                boundProvider = provider
                currentCapture = capture
                var pinchInProgress = false
                var wasPinchGesture = false
                var currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val scaleDetector = ScaleGestureDetector(context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            pinchInProgress = true
                            currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: currentZoom
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val zoomState = camera.cameraInfo.zoomState.value
                            val minZoom = zoomState?.minZoomRatio ?: 1f
                            val maxZoom = zoomState?.maxZoomRatio ?: 1f
                            currentZoom = (currentZoom * detector.scaleFactor).coerceIn(minZoom, maxZoom)
                            try { camera.cameraControl.setZoomRatio(currentZoom) } catch (_: Exception) { }
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            pinchInProgress = false
                        }
                    })
                previewView.setOnTouchListener { _, event ->
                    scaleDetector.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            pinchInProgress = true
                            wasPinchGesture = true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!wasPinchGesture && !pinchInProgress && event.pointerCount == 1) {
                                // A slightly larger metering region is more stable on small subjects.
                                // Request autofocus plus exposure/white-balance metering at the tapped area.
                                val point = previewView.meteringPointFactory.createPoint(event.x, event.y, 0.16f)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                )
                                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                                    .build()
                                try { camera.cameraControl.startFocusAndMetering(action) } catch (_: Exception) { }
                            }
                            wasPinchGesture = false
                            pinchInProgress = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            pinchInProgress = false
                            wasPinchGesture = false
                        }
                    }
                    true
                }
                onCameraReady(camera, capture)
            } catch (_: Exception) {
                // Keep the UI alive; capture reports if the camera could not be initialized.
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            previewView.setOnTouchListener(null)
            currentCapture = null
            try { boundProvider?.unbindAll() } catch (_: Exception) { }
        }
    }

    LaunchedEffect(flashMode, currentCapture) {
        try { currentCapture?.flashMode = flashMode } catch (_: Exception) { }
    }
}

private fun openAppPermissionSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) } catch (_: Exception) {
        Toast.makeText(context, "Open Android Settings → Apps → GPS Camera → Permissions", Toast.LENGTH_LONG).show()
    }
}

private fun openLocationSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) } catch (_: Exception) {
        Toast.makeText(context, "Open Android Settings and enable Location", Toast.LENGTH_LONG).show()
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private suspend fun acquireStamp(context: Context): GpsStamp = withContext(Dispatchers.IO) {
    val capturedAt = Date()
    var locationResult: android.location.Location? = null
    try {
        val fine = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine || coarse) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            // Use a recent cached fix immediately when possible; only wait briefly for a new fix.
            val cached = try { client.lastLocation.await() } catch (_: Exception) { null }
            locationResult = cached?.takeIf { System.currentTimeMillis() - it.time <= 30_000L }
            if (locationResult == null) {
                locationResult = try {
                    withTimeoutOrNull(1_400L) {
                        client.getCurrentLocation(
                            if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            null
                        ).await()
                    }
                } catch (_: Exception) { null }
            }
            if (locationResult == null) locationResult = cached
            // Avoid attaching a very old cached location to a new photo.
            if (locationResult != null && System.currentTimeMillis() - locationResult!!.time > 120_000L) {
                locationResult = null
            }
        }
    } catch (_: SecurityException) {
        locationResult = null
    }

    val location = locationResult
    val lat = location?.latitude
    val lon = location?.longitude
    // Reverse geocoding can be slow or provider-dependent; cap its wait so it cannot stall capture.
    val address = if (lat != null && lon != null) withTimeoutOrNull(900L) { reverseGeocode(context, lat, lon) } else null
    GpsStamp(
        latitude = lat,
        longitude = lon,
        address = address,
        capturedAt = capturedAt,
        accuracyMeters = location?.accuracy,
        altitudeMeters = location?.takeIf { it.hasAltitude() }?.altitude
    )
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? =
    withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(latitude, longitude, 1)
            } else {
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            addresses?.firstOrNull()?.let { a ->
                listOfNotNull(
                    a.subLocality?.takeIf { it.isNotBlank() },
                    a.locality?.takeIf { it.isNotBlank() },
                    a.subAdminArea?.takeIf { it.isNotBlank() },
                    a.adminArea?.takeIf { it.isNotBlank() },
                    a.countryName?.takeIf { it.isNotBlank() }
                ).distinct().joinToString(", ").ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

private suspend fun captureAndStamp(
    context: Context,
    capture: ImageCapture,
    stampTask: kotlinx.coroutines.Deferred<GpsStamp>,
    settings: StampSettings,
    aspectRatio: String
): Pair<Uri, GpsStamp> = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(cacheFile).build()
        val executor: Executor = ContextCompat.getMainExecutor(context)
        // Bridge CameraX's callback into a cancellable continuation.
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            capture.takePicture(output, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                }
                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(exception))
                }
            })
        }

        // The sensor capture has completed before we wait for GPS/address. This keeps shutter
        // response independent of location latency while still stamping the same captured photo.
        try {
            val stamp = stampTask.await()
            // Preserve the camera's original EXIF orientation. The renderer needs it to rotate
            // the decoded pixels correctly before drawing the stamp.
            writeExif(cacheFile, stamp, normalizeOrientation = false)
            val base = "GPS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(stamp.capturedAt)}"
            val stampedFile = File(context.cacheDir, "stamped_${System.currentTimeMillis()}.jpg")
            try {
                renderStampedImage(cacheFile, stampedFile, stamp, settings, aspectRatio)
                // The stamped bitmap has already been physically rotated to its display orientation,
                // so mark it NORMAL to prevent gallery apps from applying a second rotation.
                writeExif(stampedFile, stamp, normalizeOrientation = true)
                // Keep the stamped MediaStore URI so the native phone photo viewer can open it.
                val savedUri = saveToMediaStore(context, stampedFile, "Stamped", "${base}_stamped.jpg")
                return@withContext savedUri to stamp
            } finally {
                stampedFile.delete()
            }
        } finally {
            cacheFile.delete()
        }
    }

private fun writeExif(file: File, stamp: GpsStamp, normalizeOrientation: Boolean) {
    try {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(stamp.capturedAt))
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(stamp.capturedAt))
        // Only normalize the stamped output. Never overwrite the source camera orientation before
        // applyExifOrientation() reads it; doing so causes portrait/landscape photos to rotate.
        if (normalizeOrientation) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        }
        stamp.latitude?.let { lat ->
            stamp.longitude?.let { lon -> exif.setLatLong(lat, lon) }
        }
        stamp.altitudeMeters?.let { exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, it.toString()) }
        exif.saveAttributes()
    } catch (_: Exception) {
        // A photo is still useful even if a device's EXIF implementation rejects a field.
    }
}


private fun applyExifOrientation(file: File, source: android.graphics.Bitmap): android.graphics.Bitmap {
    val orientation = try {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = android.graphics.Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return source
    }
    return try {
        android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } catch (_: Exception) {
        source
    }
}

private fun cropToAspectRatio(source: android.graphics.Bitmap, ratioName: String): android.graphics.Bitmap {
    val targetRatio = when (ratioName) {
        "1:1" -> 1f
        "9:16" -> 9f / 16f
        "3:4" -> 3f / 4f
        else -> return source // Full screen preserves the device camera's native captured frame.
    }
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    if (kotlin.math.abs(sourceRatio - targetRatio) < 0.015f) return source
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
        cropHeight = source.height
        cropWidth = (source.height * targetRatio).toInt().coerceIn(1, source.width)
    } else {
        cropWidth = source.width
        cropHeight = (source.width / targetRatio).toInt().coerceIn(1, source.height)
    }
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    return android.graphics.Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
}

private fun renderStampedImage(source: File, destination: File, stamp: GpsStamp, settings: StampSettings, aspectRatio: String) {
    val decoded = android.graphics.BitmapFactory.decodeFile(source.absolutePath)
        ?: throw IllegalStateException("Could not process captured image")
    // CameraX may store portrait/landscape orientation in EXIF rather than rotating pixel data.
    // Normalize pixels before drawing the stamp so the saved stamped photo matches the capture orientation.
    val oriented = applyExifOrientation(source, decoded)
    if (oriented !== decoded) decoded.recycle()
    val bitmap = cropToAspectRatio(oriented, aspectRatio)
    if (bitmap !== oriented) oriented.recycle()
    var mutable: android.graphics.Bitmap? = null
    try {
        val rendered = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("Could not prepare stamped image")
        mutable = rendered
        val canvas = android.graphics.Canvas(rendered)
        val padding = (bitmap.width * 0.035f).coerceAtLeast(24f)
        val textSize = (bitmap.width * 0.027f * settings.textScale).coerceIn(16f, 58f)
        val smallTextSize = textSize * if (settings.style == "Compact") 0.72f else 0.80f
        val lines = mutableListOf<String>()
        if (settings.style != "Compact") lines.add("GATHA MEDIA")
        if (settings.showCoordinates) {
            lines.add(
                if (stamp.latitude != null && stamp.longitude != null)
                    "${formatCoordinate(stamp.latitude, true)}   ${formatCoordinate(stamp.longitude, false)}"
                else "Coordinates unavailable"
            )
        }
        if (settings.showAddress) lines.add(stamp.address ?: "Address unavailable")
        if (settings.showDateTime) lines.add(SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault()).format(stamp.capturedAt))
        val measurements = mutableListOf<String>()
        if (settings.showAccuracy) measurements.add("Accuracy: ${stamp.accuracyMeters?.let { "±%.0f m".format(Locale.US, it) } ?: "unavailable"}")
        if (settings.showAltitude) measurements.add("Altitude: ${stamp.altitudeMeters?.let { "%.0f m".format(Locale.US, it) } ?: "unavailable"}")
        if (measurements.isNotEmpty()) lines.add(measurements.joinToString(" · "))
        if (lines.isEmpty()) lines.add("GPS Camera")
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            this.textSize = textSize
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val smallPaint = android.graphics.Paint(paint).apply { this.textSize = smallTextSize }
        val lineHeights = lines.mapIndexed { index, _ -> if (index == 0 && settings.style != "Compact") textSize * 1.20f else smallTextSize * 1.24f }
        // Content-sized compact card: measured from actual text instead of a fixed-width strip.
        val iconSize = textSize * 0.92f
        val contentWidths = lines.mapIndexed { index, line ->
            val isHeading = index == 0 && settings.style != "Compact"
            (if (isHeading) paint else smallPaint).measureText(line)
        }
        val maxTextWidth = contentWidths.maxOrNull() ?: textSize * 4f
        val maxCardWidth = minOf((bitmap.width - padding * 2).coerceAtLeast(1f), (bitmap.width * 0.46f).coerceAtLeast(1f))
        val desiredWidth = maxTextWidth + padding * 1.15f + iconSize + padding * 0.35f
        val cardWidth = desiredWidth.coerceAtMost(maxCardWidth).coerceAtLeast((bitmap.width * 0.18f).coerceAtMost(maxCardWidth))
        val cardHeight = lineHeights.sum() + padding * 0.72f
        val left = when (settings.position) {
            "Top Right", "Bottom Right" -> bitmap.width - cardWidth - padding
            "Top Middle", "Bottom Middle" -> (bitmap.width - cardWidth) / 2f
            else -> padding
        }.coerceIn(0f, (bitmap.width - cardWidth).coerceAtLeast(0f))
        val top = when (settings.position) {
            "Top Left", "Top Middle", "Top Right" -> padding
            else -> bitmap.height - cardHeight - padding
        }.coerceIn(0f, (bitmap.height - cardHeight).coerceAtLeast(0f))
        val right = left + cardWidth
        val bottom = (top + cardHeight).coerceAtMost(bitmap.height.toFloat())
        val panelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((settings.opacity * 255).toInt().coerceIn(0, 255), 12, 14, 18)
        }
        val radius = (textSize * 0.42f).coerceAtLeast(8f)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, panelPaint)

        // Small location-pin mark inside the card, drawn with native Canvas to avoid extra dependencies.
        val pinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (textSize * 0.09f).coerceAtLeast(2f)
        }
        val pinCx = left + padding * 0.45f + iconSize * 0.5f
        val pinCy = top + padding * 0.42f + iconSize * 0.40f
        val pinRadius = iconSize * 0.28f
        canvas.drawCircle(pinCx, pinCy - iconSize * 0.08f, pinRadius, pinPaint)
        val pinPath = android.graphics.Path().apply {
            moveTo(pinCx - pinRadius * 0.72f, pinCy + pinRadius * 0.48f)
            lineTo(pinCx, pinCy + iconSize * 0.43f)
            lineTo(pinCx + pinRadius * 0.72f, pinCy + pinRadius * 0.48f)
        }
        canvas.drawPath(pinPath, pinPaint)
        val innerWidth = (cardWidth - iconSize - padding * 1.35f).coerceAtLeast(1f)
        val align = android.graphics.Paint.Align.LEFT
        paint.textAlign = align
        smallPaint.textAlign = align
        val textX = left + iconSize + padding * 0.48f
        var y = top + padding * 0.38f + textSize * 0.82f
        lines.forEachIndexed { index, line ->
            val isHeading = index == 0 && settings.style != "Compact"
            val p = if (isHeading || (settings.showCoordinates && index == if (settings.style == "Compact") 0 else 1)) paint else smallPaint
            canvas.drawText(ellipsizeLine(line, p, innerWidth), textX, y, p)
            y += lineHeights[index]
        }
        FileOutputStream(destination).use { out ->
            if (!rendered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, out)) {
                throw IllegalStateException("Could not write stamped image")
            }
        }
    } finally {
        mutable?.recycle()
        bitmap.recycle()
    }
}

private fun ellipsizeLine(text: String, paint: android.graphics.Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val suffix = "…"
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) end--
    return if (end <= 0) suffix else text.substring(0, end) + suffix
}

private fun saveToMediaStore(context: Context, file: File, folder: String, name: String): Uri {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS Camera/$folder")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Could not create photo in gallery")
    try {
        resolver.openOutputStream(uri, "w")?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not write photo to gallery")
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        return uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

/** Finds the newest stamped image saved by GPS Camera, including after an app restart. */
private fun findLatestStampedPhoto(context: Context): Uri? {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
    val selectionArgs = arrayOf("Pictures/GPS Camera/Stamped/")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    return try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Opens the phone's gallery app itself. GPS Camera only captures and saves photos;
 * browsing and viewing are left to the gallery app. If the device exposes no app
 * through CATEGORY_APP_GALLERY, fall back to opening the latest saved image directly.
 */
private fun openInNativePhotoViewer(context: Context, uri: Uri) {
    val galleryIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_APP_GALLERY)
    }
    try {
        try {
            context.startActivity(galleryIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            val imageIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(imageIntent)
        }
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No compatible gallery app is installed", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open gallery: ${e.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
    }
}

private fun formatCoordinate(value: Double, latitude: Boolean): String {
    val direction = if (latitude) {
        if (value >= 0) "N" else "S"
    } else {
        if (value >= 0) "E" else "W"
    }
    return "%.6f° %s".format(Locale.US, kotlin.math.abs(value), direction)
}
