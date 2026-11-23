package com.example.pixelftp.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLEncoder

object LaptopRepository {

    suspend fun autoDiscoverLaptop(timeoutMs: Int = 2500): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = timeoutMs
                bind(InetSocketAddress(8889))
            }
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val text = String(packet.data, 0, packet.length)
            val json = JSONObject(text)
            if (json.optString("service") == "pixelftp_laptop") {
                return@withContext json.optString("ip")
            }
        } catch (_: Exception) {
            // Timeout or network error
        } finally {
            socket?.close()
        }
        null
    }

    suspend fun checkStatus(ip: String, port: Int = 8080): Result<LaptopStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/api/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                Result.success(
                    LaptopStatus(
                        hostname = json.optString("hostname", "Laptop"),
                        user = json.optString("user", "User"),
                        os = json.optString("os", "Windows")
                    )
                )
            } else {
                Result.failure(Exception("HTTP Error ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShortcuts(ip: String, port: Int = 8080): Result<Pair<List<ShortcutItem>, List<ShortcutItem>>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/api/shortcuts")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val shortcuts = mutableListOf<ShortcutItem>()
                val scArr = json.optJSONArray("shortcuts")
                if (scArr != null) {
                    for (i in 0 until scArr.length()) {
                        val item = scArr.getJSONObject(i)
                        shortcuts.add(
                            ShortcutItem(
                                name = item.getString("name"),
                                path = item.getString("path"),
                                icon = item.optString("icon", "folder")
                            )
                        )
                    }
                }

                val drives = mutableListOf<ShortcutItem>()
                val drArr = json.optJSONArray("drives")
                if (drArr != null) {
                    for (i in 0 until drArr.length()) {
                        val item = drArr.getJSONObject(i)
                        drives.add(
                            ShortcutItem(
                                name = item.getString("name"),
                                path = item.getString("path"),
                                icon = "drive"
                            )
                        )
                    }
                }

                Result.success(Pair(shortcuts, drives))
            } else {
                Result.failure(Exception("HTTP Error ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDirectory(ip: String, port: Int = 8080, path: String? = null): Result<DirectoryResponse> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = if (path != null) URLEncoder.encode(path, "UTF-8") else ""
            val url = URL("http://$ip:$port/api/list?path=$encodedPath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.has("error")) {
                    return@withContext Result.failure(Exception(json.getString("error")))
                }

                val currentPath = json.getString("currentPath")
                val parentPath = if (json.isNull("parentPath")) null else json.optString("parentPath")

                val items = mutableListOf<FileItem>()
                val itemsArr = json.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val obj = itemsArr.getJSONObject(i)
                        items.add(
                            FileItem(
                                name = obj.getString("name"),
                                path = obj.getString("path"),
                                isDir = obj.getBoolean("isDir"),
                                size = obj.optLong("size", 0L),
                                modified = obj.optLong("modified", 0L)
                            )
                        )
                    }
                }

                Result.success(DirectoryResponse(currentPath, parentPath, items))
            } else {
                Result.failure(Exception("HTTP Error ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        ip: String,
        port: Int = 8080,
        remotePath: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = URLEncoder.encode(remotePath, "UTF-8")
            val url = URL("http://$ip:$port/api/download?path=$encodedPath")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 30000
                requestMethod = "GET"
            }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("Gagal mendownload: HTTP ${conn.responseCode}"))
            }

            val totalBytes = conn.contentLengthLong
            val rawName = remotePath.substringAfterLast('\\').substringAfterLast('/')
            val safeName = rawName.ifBlank { "downloaded_file" }

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PixelFTP"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            var targetFile = File(downloadDir, safeName)
            if (targetFile.exists()) {
                val dotIndex = safeName.lastIndexOf('.')
                val base = if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
                val ext = if (dotIndex > 0) safeName.substring(dotIndex) else ""
                var counter = 1
                while (targetFile.exists()) {
                    targetFile = File(downloadDir, "$base ($counter)$ext")
                    counter++
                }
            }

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        onProgress(totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
