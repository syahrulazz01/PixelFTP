package com.example.pixelftp.ui.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.pixelftp.data.FileItem
import com.example.pixelftp.data.LaptopRepository
import com.example.pixelftp.data.LaptopStatus
import com.example.pixelftp.data.ShortcutItem
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopFileManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pixelftp_prefs", Context.MODE_PRIVATE) }

    var laptopIp by remember {
        mutableStateOf(prefs.getString("laptop_ip", "192.168.1.11") ?: "192.168.1.11")
    }
    var showIpDialog by remember { mutableStateOf(false) }
    var tempIpInput by remember { mutableStateOf(laptopIp) }

    var isConnected by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var laptopStatus by remember { mutableStateOf<LaptopStatus?>(null) }

    var shortcuts by remember { mutableStateOf<List<ShortcutItem>>(emptyList()) }
    var drives by remember { mutableStateOf<List<ShortcutItem>>(emptyList()) }

    var currentPath by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf<String?>(null) }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    // Download state
    var downloadingFileName by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadBytesText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Function to load directory
    fun loadDirectory(path: String?) {
        scope.launch {
            isLoadingFiles = true
            errorMessage = null
            val result = LaptopRepository.listDirectory(laptopIp, 8080, path)
            result.onSuccess { dirResponse ->
                currentPath = dirResponse.currentPath
                parentPath = dirResponse.parentPath
                fileItems = dirResponse.items
                isLoadingFiles = false
            }.onFailure { err ->
                errorMessage = err.message ?: "Gagal memuat folder"
                isLoadingFiles = false
            }
        }
    }

    // Function to connect to laptop
    fun connectToLaptop(ip: String) {
        scope.launch {
            isCheckingConnection = true
            errorMessage = null
            val statusResult = LaptopRepository.checkStatus(ip, 8080)
            statusResult.onSuccess { status ->
                laptopStatus = status
                isConnected = true
                laptopIp = ip
                prefs.edit().putString("laptop_ip", ip).apply()

                // Load shortcuts
                val scResult = LaptopRepository.getShortcuts(ip, 8080)
                scResult.onSuccess { (scList, drList) ->
                    shortcuts = scList
                    drives = drList
                }

                // Load initial directory (Downloads or User home)
                loadDirectory(null)
                isCheckingConnection = false
            }.onFailure { err ->
                isConnected = false
                isCheckingConnection = false
                errorMessage = "Tidak dapat terhubung ke laptop ($ip:8080). Pastikan 'Mulai Server Laptop.bat' sedang berjalan di laptop."
            }
        }
    }

    // Auto-discover on start
    LaunchedEffect(Unit) {
        // Try quick connection to current IP first
        val quickTest = LaptopRepository.checkStatus(laptopIp, 8080)
        if (quickTest.isSuccess) {
            connectToLaptop(laptopIp)
        } else {
            // Try auto-discovery
            val discovered = LaptopRepository.autoDiscoverLaptop(1500)
            if (discovered != null) {
                laptopIp = discovered
                connectToLaptop(discovered)
            } else {
                connectToLaptop(laptopIp)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Jelajahi Laptop",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isConnected) "🟢 Terhubung: ${laptopStatus?.hostname ?: laptopIp}" else "🔴 Belum Terhubung ($laptopIp)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tempIpInput = laptopIp
                        showIpDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ganti IP Laptop")
                    }
                    IconButton(onClick = { connectToLaptop(laptopIp) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Downloading Card Indicator
            AnimatedVisibility(visible = downloadingFileName != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mendownload: ${downloadingFileName ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = downloadBytesText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Shortcuts & Drives Chips (Horizontal Scroll)
            if (shortcuts.isNotEmpty() || drives.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drives first
                    drives.forEach { drive ->
                        AssistChip(
                            onClick = { loadDirectory(drive.path) },
                            label = { Text(drive.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    // System Shortcuts
                    shortcuts.forEach { sc ->
                        val icon = when (sc.icon) {
                            "download" -> Icons.Default.Download
                            "desktop" -> Icons.Default.Computer
                            "document" -> Icons.Default.Description
                            "image" -> Icons.Default.Image
                            "video" -> Icons.Default.Movie
                            "music" -> Icons.Default.MusicNote
                            else -> Icons.Default.Folder
                        }
                        AssistChip(
                            onClick = { loadDirectory(sc.path) },
                            label = { Text(sc.name) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }

            // Navigation Bar (Current Path + Back Button)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (parentPath != null) {
                                loadDirectory(parentPath)
                            }
                        },
                        enabled = parentPath != null && !isLoadingFiles
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Folder Induk (Ke Atas)"
                        )
                    }
                    Text(
                        text = currentPath.ifEmpty { "Memuat lokasi..." },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = { loadDirectory(currentPath.ifEmpty { null }) },
                        enabled = !isLoadingFiles
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Segarkan Folder")
                    }
                }
            }

            // Search filter bar
            if (fileItems.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari berkas di folder ini...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Body: Loading, Error, or File List
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isCheckingConnection || (isLoadingFiles && fileItems.isEmpty())) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isCheckingConnection) "Menghubungkan ke laptop..." else "Membaca berkas di laptop...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak Dapat Mengakses Laptop",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { connectToLaptop(laptopIp) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Coba Lagi")
                            }
                            OutlinedButton(onClick = {
                                tempIpInput = laptopIp
                                showIpDialog = true
                            }) {
                                Text("Ubah IP")
                            }
                        }
                    }
                } else {
                    val filteredItems = remember(fileItems, searchQuery) {
                        if (searchQuery.isBlank()) fileItems
                        else fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Tidak ada berkas yang cocok" else "Folder ini kosong",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredItems, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    onFolderClick = { loadDirectory(item.path) },
                                    onDownloadClick = {
                                        scope.launch {
                                            downloadingFileName = item.name
                                            downloadProgress = 0f
                                            downloadBytesText = "Memulai download..."

                                            val result = LaptopRepository.downloadFile(
                                                ip = laptopIp,
                                                port = 8080,
                                                remotePath = item.path,
                                                onProgress = { downloaded, total ->
                                                    val fraction = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                                                    downloadProgress = fraction
                                                    downloadBytesText = "${formatFileSize(downloaded)} / ${formatFileSize(total)} (${(fraction * 100).toInt()}%)"
                                                }
                                            )

                                            downloadingFileName = null
                                            result.onSuccess { downloadedFile ->
                                                val action = snackbarHostState.showSnackbar(
                                                    message = "✅ ${item.name} berhasil disimpan!",
                                                    actionLabel = "Buka File",
                                                    duration = SnackbarDuration.Long
                                                )
                                                if (action == SnackbarResult.ActionPerformed) {
                                                    openFile(context, downloadedFile)
                                                }
                                            }.onFailure { err ->
                                                Toast.makeText(
                                                    context,
                                                    "Gagal mendownload: ${err.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // IP Configuration Dialog
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Pengaturan Alamat Laptop") },
            text = {
                Column {
                    Text(
                        text = "Masukkan alamat IP Laptop di jaringan Wi-Fi lokal:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempIpInput,
                        onValueChange = { tempIpInput = it.trim() },
                        label = { Text("IP Laptop (contoh: 192.168.1.11)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tips: Pastikan port 8080 aktif melalui file 'Mulai Server Laptop.bat' di laptop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showIpDialog = false
                    connectToLaptop(tempIpInput)
                }) {
                    Text("Hubungkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun FileListItem(
    item: FileItem,
    onFolderClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.isDir) onFolderClick()
                else onDownloadClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, iconColor) = getFileVisuals(item)

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (item.isDir) "Folder" else "${formatFileSize(item.size)} • ${formatDate(item.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (item.isDir) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka Folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        } else {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download File",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun getFileVisuals(item: FileItem): Pair<ImageVector, Color> {
    if (item.isDir) {
        return Pair(Icons.Default.Folder, Color(0xFFFFB300)) // Amber folder
    }

    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Pair(Icons.Default.Image, Color(0xFF4CAF50)) // Green
        "mp4", "mkv", "avi", "mov", "webm", "flv" -> Pair(Icons.Default.Movie, Color(0xFF9C27B0)) // Purple
        "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Pair(Icons.Default.MusicNote, Color(0xFFFF9800)) // Orange
        "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Default.Archive, Color(0xFF795548)) // Brown
        "pdf" -> Pair(Icons.Default.Description, Color(0xFFF44336)) // Red
        "doc", "docx", "txt", "rtf" -> Pair(Icons.Default.Description, Color(0xFF2196F3)) // Blue
        "xls", "xlsx", "csv" -> Pair(Icons.Default.Description, Color(0xFF388E3C)) // Dark Green
        "ppt", "pptx" -> Pair(Icons.Default.Description, Color(0xFFE64A19)) // Deep Orange
        "exe", "msi", "bat", "cmd", "apk" -> Pair(Icons.Default.Terminal, Color(0xFF607D8B)) // Blue Gray
        "kt", "java", "py", "js", "html", "css", "json", "xml" -> Pair(Icons.Default.Code, Color(0xFF00ACC1)) // Cyan
        else -> Pair(Icons.Default.InsertDriveFile, Color(0xFF78909C)) // Gray
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun openFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val ext = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
