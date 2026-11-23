package com.example.pixelftp.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.pixelftp.MainActivity
import com.example.pixelftp.R
import com.example.pixelftp.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FtpService : Service() {

    private var ftpServer: FtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val ACTION_START = "com.example.pixelftp.ACTION_START"
        const val ACTION_STOP = "com.example.pixelftp.ACTION_STOP"
        const val CHANNEL_ID = "PixelFtpChannel"
        const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _serverUrl = MutableStateFlow<String?>(null)
        val serverUrl = _serverUrl.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs = _logs.asStateFlow()

        fun addLog(msg: String) {
            val current = _logs.value.toMutableList()
            if (current.size > 50) current.removeAt(0)
            current.add(msg)
            _logs.value = current
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServer()
            ACTION_START, null -> startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (_isRunning.value) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PixelFTP:WakeLock")?.apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PixelFTP:WifiLock")?.apply {
            acquire()
        }

        val ip = NetworkUtils.getLocalIpAddress(this) ?: "127.0.0.1"
        val port = 2121
        val url = "ftp://$ip:$port"
        _serverUrl.value = url

        ftpServer = FtpServer(port = port) { msg ->
            addLog(msg)
        }
        ftpServer?.start()
        _isRunning.value = true

        val notification = buildNotification(url)
        startForeground(NOTIFICATION_ID, notification)
        addLog("Server aktif di: $url")
    }

    private fun stopServer() {
        ftpServer?.stop()
        ftpServer = null
        _isRunning.value = false
        _serverUrl.value = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        addLog("Server dinonaktifkan.")
    }

    private fun buildNotification(url: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FtpService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PixelFTP Server Berjalan")
            .setContentText(url)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FTP Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat PixelFTP Server aktif di latar belakang"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
