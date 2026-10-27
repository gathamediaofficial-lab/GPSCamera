package com.gatha.gpscamera

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GalleryBg = Color(0xFF101113)
private val GalleryCard = Color(0xFF1B1D20)
private val GalleryMuted = Color(0xFFB6BAC3)

data class GalleryPhoto(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val dateTaken: Long,
    val sizeBytes: Long,
) {
    val isStamped: Boolean get() = name.contains("_stamped", ignoreCase = true) ||
        relativePath.contains("/Stamped", ignoreCase = true)
    val kind: String get() = if (isStamped) "Stamped" else "Original"
}

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 17, 19)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 17, 19)),
        )
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GalleryScreen { finish() } } }
    }
}

@Composable
private fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(value = true) }
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<GalleryPhoto?>(null) }
    var confirmDelete by remember { mutableStateOf<GalleryPhoto?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        photos = withContext(Dispatchers.IO) { loadGpsCameraPhotos(context) }
        isLoading = false
    }
    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        containerColor = GalleryBg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(GalleryBg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Your Photos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("${photos.size} photos · GPS Camera", color = GalleryMuted, fontSize = 12.sp)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(GalleryBg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Stamped", "Original").forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8F0FF),
                            selectedLabelColor = GalleryBg,
                            labelColor = Color.White
                        )
                    )
                }
            }
            val shown = photos.filter { photo ->
                when (filter) {
                    "Stamped" -> photo.isStamped
                    "Original" -> !photo.isStamped
                    else -> true
                }
            }
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                shown.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = GalleryMuted, modifier = Modifier.size(36.dp))
                        Text(if (photos.isEmpty()) "No GPS Camera photos yet" else "No ${filter.lowercase()} photos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("Capture a photo and it will appear here. Original and stamped images are stored separately.", color = GalleryMuted, fontSize = 13.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.uri.toString() }) { photo ->
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(GalleryCard)
                                .clickable { selected = photo }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.78f),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                },
                                update = { view -> view.setImageURI(photo.uri) }
                            )
                            Text(
                                photo.kind,
                                color = if (photo.isStamped) Color(0xFFE8F0FF) else GalleryMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, top = 7.dp, bottom = 2.dp)
                            )
                            Text(
                                SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(photo.dateTaken)),
                                color = GalleryMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { photo ->
        var showDetails by remember(photo.uri) { mutableStateOf(value = false) }
        val currentIndex = photos.indexOfFirst { it.uri == photo.uri }.coerceAtLeast(0)
        Dialog(
            onDismissRequest = { selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF050506))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xEE101113)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(photo.kind + " photo", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${currentIndex + 1} of ${photos.size}", color = GalleryMuted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(Icons.Default.Info, contentDescription = "Photo details", tint = Color.White)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> ImageView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            adjustViewBounds = true
                        } },
                        update = { view -> view.setImageURI(photo.uri) }
                    )
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = { selected = photos[currentIndex - 1] },
                            modifier = Modifier.align(Alignment.CenterStart).padding(8.dp).background(Color(0x88000000), RoundedCornerShape(50))
                        ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous photo", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    }
                    if (currentIndex < photos.lastIndex) {
                        IconButton(
                            onClick = { selected = photos[currentIndex + 1] },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp).background(Color(0x88000000), RoundedCornerShape(50))
                        ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next photo", tint = Color.White, modifier = Modifier.size(34.dp)) }
                    }
                }
                if (showDetails) {
                    Column(
                        Modifier.fillMaxWidth().background(GalleryCard).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(photo.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Captured: ${SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(photo.dateTaken))}", color = GalleryMuted, fontSize = 12.sp)
                        Text("Size: ${formatBytes(photo.sizeBytes)}", color = GalleryMuted, fontSize = 12.sp)
                        Text("Folder: ${photo.relativePath}", color = GalleryMuted, fontSize = 11.sp)
                        Text("GPS metadata is available only when a valid location was captured.", color = GalleryMuted, fontSize = 11.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF101113)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        try {
                            val mime = context.contentResolver.getType(photo.uri) ?: "image/jpeg"
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, photo.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share photo"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Unable to share this photo", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp)); Text("Share", color = Color.White)
                    }
                    TextButton(onClick = { confirmDelete = photo }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF8A80))
                        Spacer(Modifier.width(6.dp)); Text("Delete", color = Color(0xFFFF8A80))
                    }
                }
            }
        }
    }

    confirmDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = GalleryCard,
            title = { Text("Delete photo?", color = Color.White) },
            text = { Text("This permanently deletes ${photo.name}. This action cannot be undone.", color = GalleryMuted) },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = try { context.contentResolver.delete(photo.uri, null, null) > 0 } catch (_: Exception) { false }
                    if (deleted) {
                        Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                        selected = null
                        refreshKey++
                    } else {
                        Toast.makeText(context, "Could not delete photo", Toast.LENGTH_SHORT).show()
                    }
                    confirmDelete = null
                }) { Text("Delete", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = GalleryMuted) } }
        )
    }
}

private fun loadGpsCameraPhotos(context: android.content.Context): List<GalleryPhoto> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.SIZE
    )
    val result = mutableListOf<GalleryPhoto>()
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("Pictures/GPS Camera/%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            result += GalleryPhoto(
                uri = ContentUris.withAppendedId(collection, id),
                name = cursor.getString(nameCol) ?: "Photo",
                relativePath = cursor.getString(pathCol) ?: "Pictures/GPS Camera",
                dateTaken = cursor.getLong(dateCol) * 1000L,
                sizeBytes = cursor.getLong(sizeCol)
            )
        }
    }
    return result
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < (1024 * 1024) -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}
