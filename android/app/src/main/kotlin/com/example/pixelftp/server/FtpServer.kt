package com.example.pixelftp.server

import android.os.Environment
import android.util.Log
import java.io.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class FtpServer(
    private val port: Int = 2121,
    private val rootDir: File = Environment.getExternalStorageDirectory(),
    private val onLog: (String) -> Unit = {}
) {
    private val tag = "FtpServer"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning) return
        isRunning = true
        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                log("FTP Server started on port $port, root: ${rootDir.absolutePath}")
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        log("Client connected from: ${clientSocket.inetAddress.hostAddress}")
                        threadPool.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                        log("Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log("Server start failed: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        log("FTP Server stopped.")
    }

    private fun log(msg: String) {
        Log.d(tag, msg)
        onLog(msg)
    }

    private fun handleClient(clientSocket: Socket) {
        var currentDir = rootDir
        var passiveServerSocket: ServerSocket? = null
        var activeDataHost: String? = null
        var activeDataPort: Int? = null
        var renameFrom: File? = null
        var isBinary = true

        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream(), Charsets.UTF_8))

            fun sendReply(code: Int, text: String) {
                val line = "$code $text\r\n"
                writer.write(line)
                writer.flush()
                log("-> $code $text")
            }

            fun resolve(param: String): File {
                val cleaned = param.trim()
                return when {
                    cleaned.isEmpty() -> currentDir
                    cleaned.startsWith("/") -> {
                        val relative = cleaned.trimStart('/')
                        File(rootDir, relative)
                    }
                    else -> File(currentDir, cleaned)
                }
            }

            fun getVirtualPath(file: File): String {
                val rootPath = rootDir.absolutePath
                val filePath = file.absolutePath
                return if (filePath.startsWith(rootPath)) {
                    val sub = filePath.substring(rootPath.length).replace(File.separatorChar, '/')
                    if (sub.isEmpty()) "/" else sub
                } else {
                    "/"
                }
            }

            fun openDataConnection(): Socket? {
                return try {
                    if (passiveServerSocket != null) {
                        val sock = passiveServerSocket?.accept()
                        passiveServerSocket?.close()
                        passiveServerSocket = null
                        sock
                    } else if (activeDataHost != null && activeDataPort != null) {
                        val sock = Socket(activeDataHost, activeDataPort!!)
                        activeDataHost = null
                        activeDataPort = null
                        sock
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    log("Error opening data connection: ${e.message}")
                    null
                }
            }

            sendReply(220, "PixelFTP Server Ready.")

            while (isRunning) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val spaceIdx = trimmed.indexOf(' ')
                val cmd = if (spaceIdx != -1) trimmed.substring(0, spaceIdx).uppercase(Locale.US) else trimmed.uppercase(Locale.US)
                val arg = if (spaceIdx != -1) trimmed.substring(spaceIdx + 1).trim() else ""

                log("<- $cmd $arg")

                when (cmd) {
                    "USER" -> sendReply(230, "User logged in, proceed.")
                    "PASS" -> sendReply(230, "User logged in, proceed.")
                    "SYST" -> sendReply(215, "UNIX Type: L8")
                    "FEAT" -> {
                        writer.write("211-Features:\r\n UTF8\r\n SIZE\r\n MDTM\r\n211 End\r\n")
                        writer.flush()
                    }
                    "OPTS" -> {
                        if (arg.uppercase(Locale.US).startsWith("UTF8")) {
                            sendReply(200, "Always in UTF8 mode.")
                        } else {
                            sendReply(502, "Command not implemented.")
                        }
                    }
                    "PWD", "XPWD" -> sendReply(257, "\"${getVirtualPath(currentDir)}\" is current directory.")
                    "TYPE" -> {
                        isBinary = arg.uppercase(Locale.US) != "A"
                        sendReply(200, "Type set to ${if (isBinary) "Binary (I)" else "ASCII (A)"}.")
                    }
                    "PASV" -> {
                        passiveServerSocket?.close()
                        val pasv = ServerSocket(0)
                        pasv.soTimeout = 20000
                        passiveServerSocket = pasv

                        val localAddr = clientSocket.localAddress
                        val ipBytes = if (localAddr is Inet4Address) {
                            localAddr.address
                        } else {
                            InetAddress.getByName("127.0.0.1").address
                        }
                        val p = pasv.localPort
                        val p1 = (p shr 8) and 0xff
                        val p2 = p and 0xff
                        val ipStr = "${ipBytes[0].toInt() and 0xff},${ipBytes[1].toInt() and 0xff},${ipBytes[2].toInt() and 0xff},${ipBytes[3].toInt() and 0xff}"
                        sendReply(227, "Entering Passive Mode ($ipStr,$p1,$p2).")
                    }
                    "EPSV" -> {
                        passiveServerSocket?.close()
                        val pasv = ServerSocket(0)
                        pasv.soTimeout = 20000
                        passiveServerSocket = pasv
                        sendReply(229, "Entering Extended Passive Mode (|||${pasv.localPort}|).")
                    }
                    "PORT" -> {
                        try {
                            val parts = arg.split(",").map { it.trim().toInt() }
                            if (parts.size == 6) {
                                activeDataHost = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
                                activeDataPort = (parts[4] shl 8) + parts[5]
                                sendReply(200, "PORT command successful.")
                            } else {
                                sendReply(501, "Syntax error in parameters.")
                            }
                        } catch (e: Exception) {
                            sendReply(501, "Syntax error in parameters.")
                        }
                    }
                    "CWD", "XCWD" -> {
                        val target = resolve(arg)
                        if (target.exists() && target.isDirectory) {
                            currentDir = target
                            sendReply(250, "Directory successfully changed to \"${getVirtualPath(currentDir)}\".")
                        } else {
                            sendReply(550, "Failed to change directory.")
                        }
                    }
                    "CDUP", "XCUP" -> {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.absolutePath.startsWith(rootDir.absolutePath)) {
                            currentDir = parent
                        } else {
                            currentDir = rootDir
                        }
                        sendReply(200, "Directory successfully changed.")
                    }
                    "LIST", "NLST", "MLSD" -> {
                        val target = if (arg.isEmpty()) currentDir else resolve(arg)
                        val dataSock = openDataConnection()
                        if (dataSock == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }
                        sendReply(150, "Here comes the directory listing.")
                        try {
                            val dataWriter = BufferedWriter(OutputStreamWriter(dataSock.getOutputStream(), Charsets.UTF_8))
                            val files = if (target.isDirectory) target.listFiles() ?: emptyArray() else arrayOf(target)
                            val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

                            for (f in files) {
                                if (cmd == "NLST") {
                                    dataWriter.write("${f.name}\r\n")
                                } else {
                                    val isDir = f.isDirectory
                                    val perms = if (isDir) "drwxrwxrwx" else "-rw-rw-rw-"
                                    val size = if (isDir) 0L else f.length()
                                    val dateStr = dateFormat.format(Date(f.lastModified()))
                                    dataWriter.write(String.format(Locale.US, "%s   1 ftp      ftp  %10d %s %s\r\n", perms, size, dateStr, f.name))
                                }
                            }
                            dataWriter.flush()
                        } catch (e: Exception) {
                            log("Error during list: ${e.message}")
                        } finally {
                            dataSock.close()
                        }
                        sendReply(226, "Directory send OK.")
                    }
                    "RETR" -> {
                        val target = resolve(arg)
                        if (!target.exists() || target.isDirectory) {
                            sendReply(550, "File not found or is a directory.")
                            continue
                        }
                        val dataSock = openDataConnection()
                        if (dataSock == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }
                        sendReply(150, "Opening data connection for ${target.name} (${target.length()} bytes).")
                        try {
                            FileInputStream(target).use { input ->
                                dataSock.getOutputStream().use { output ->
                                    val buf = ByteArray(65536)
                                    var read: Int
                                    while (input.read(buf).also { read = it } != -1) {
                                        output.write(buf, 0, read)
                                    }
                                    output.flush()
                                }
                            }
                            sendReply(226, "Transfer complete.")
                        } catch (e: Exception) {
                            log("Error during RETR: ${e.message}")
                            sendReply(426, "Transfer aborted.")
                        } finally {
                            dataSock.close()
                        }
                    }
                    "STOR", "APPE" -> {
                        val target = resolve(arg)
                        val append = (cmd == "APPE")
                        val dataSock = openDataConnection()
                        if (dataSock == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }
                        sendReply(150, "Ready to receive data.")
                        try {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target, append).use { fos ->
                                dataSock.getInputStream().use { fis ->
                                    val buf = ByteArray(65536)
                                    var read: Int
                                    while (fis.read(buf).also { read = it } != -1) {
                                        fos.write(buf, 0, read)
                                    }
                                    fos.flush()
                                }
                            }
                            sendReply(226, "Transfer complete.")
                        } catch (e: Exception) {
                            log("Error during STOR: ${e.message}")
                            sendReply(426, "Transfer aborted.")
                        } finally {
                            dataSock.close()
                        }
                    }
                    "DELE" -> {
                        val target = resolve(arg)
                        if (target.exists() && !target.isDirectory && target.delete()) {
                            sendReply(250, "File deleted successfully.")
                        } else {
                            sendReply(550, "Delete failed.")
                        }
                    }
                    "MKD", "XMKD" -> {
                        val target = resolve(arg)
                        if (target.mkdirs() || target.exists()) {
                            sendReply(257, "\"${getVirtualPath(target)}\" created.")
                        } else {
                            sendReply(550, "Create directory failed.")
                        }
                    }
                    "RMD", "XRMD" -> {
                        val target = resolve(arg)
                        if (target.exists() && target.isDirectory && target.deleteRecursively()) {
                            sendReply(250, "Directory removed.")
                        } else {
                            sendReply(550, "Remove directory failed.")
                        }
                    }
                    "RNFR" -> {
                        val target = resolve(arg)
                        if (target.exists()) {
                            renameFrom = target
                            sendReply(350, "Requested file action pending further information.")
                        } else {
                            sendReply(550, "File not found.")
                        }
                    }
                    "RNTO" -> {
                        if (renameFrom != null) {
                            val target = resolve(arg)
                            if (renameFrom!!.renameTo(target)) {
                                sendReply(250, "File renamed successfully.")
                            } else {
                                sendReply(550, "Rename failed.")
                            }
                            renameFrom = null
                        } else {
                            sendReply(503, "Bad sequence of commands.")
                        }
                    }
                    "SIZE" -> {
                        val target = resolve(arg)
                        if (target.exists() && !target.isDirectory) {
                            sendReply(213, "${target.length()}")
                        } else {
                            sendReply(550, "Could not get file size.")
                        }
                    }
                    "MDTM" -> {
                        val target = resolve(arg)
                        if (target.exists()) {
                            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                            sendReply(213, sdf.format(Date(target.lastModified())))
                        } else {
                            sendReply(550, "Could not get file modification time.")
                        }
                    }
                    "NOOP" -> sendReply(200, "OK")
                    "QUIT" -> {
                        sendReply(221, "Goodbye.")
                        break
                    }
                    else -> sendReply(502, "Command not implemented ($cmd).")
                }
            }
        } catch (e: Exception) {
            log("Client error: ${e.message}")
        } finally {
            passiveServerSocket?.close()
            try { clientSocket.close() } catch (_: Exception) {}
            log("Client disconnected.")
        }
    }
}
