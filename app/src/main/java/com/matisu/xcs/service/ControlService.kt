package com.matisu.xcs.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.matisu.xcs.App
import com.matisu.xcs.ScreenCaptureActivity
import com.matisu.xcs.server.ControlServer
import com.matisu.xcs.server.ScreenCapturer

class ControlService : Service() {

    private var server: ControlServer? = null
    private var screenCapturer: ScreenCapturer? = null
    private var touchService: TouchService? = null
    private var mediaProjectionToken: android.content.Intent? = null

    companion object {
        const val ACTION_START = "com.matisu.xcs.action.START"
        const val ACTION_STOP = "com.matisu.xcs.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, ControlService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ControlService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopSelf()
            ScreenCaptureActivity.ACTION_RESULT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mediaProjectionToken = data
                    startScreenCapture()
                }
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return
        server = ControlServer(this).apply {
            setTouchService(touchService)
            onScreenCaptureRequest = { format, quality ->
                screenCapturer?.capture(format, quality)
            }
        }
        try {
            server!!.start()
            isRunning = true
            updateNotification()

            if (mediaProjectionToken != null) {
                startScreenCapture()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startScreenCapture() {
        val token = mediaProjectionToken ?: return
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpManager.getMediaProjection(Activity.RESULT_OK, token.cloneFilter())
        val metrics = resources.displayMetrics

        screenCapturer?.close()
        screenCapturer = ScreenCapturer(metrics, projection)
        server?.setScreenCapturer(screenCapturer)
    }

    fun bindTouchService(service: TouchService) {
        touchService = service
        server?.setTouchService(service)
    }

    fun unbindTouchService() {
        touchService = null
        server?.setTouchService(null)
    }

    private fun startForeground() {
        val notification = buildNotification("服务启动中...")
        startForeground(App.NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        val ip = getLocalIpAddress()
        val port = server?.listeningPort ?: 8888
        val text = "运行中 | http://$ip:$port"
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(App.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, com.matisu.xcs.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("MatisuXCS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (_: Exception) {
            return "0.0.0.0"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        screenCapturer?.close()
        server?.destroy()
        server = null
        isRunning = false
        super.onDestroy()
    }
}
