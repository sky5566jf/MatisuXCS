package com.matisu.xcs

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.matisu.xcs.service.ControlService
import com.matisu.xcs.service.TouchService

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val running = ControlService.isRunning
        val ip = getLocalIpAddress()

        statusText.text = if (running) "● 服务运行中" else "○ 服务未启动"
        statusText.setTextColor(if (running) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())

        ipText.text = if (running) {
            "http://$ip:8888"
        } else {
            "等待启动..."
        }

        startBtn.isEnabled = !running
        stopBtn.isEnabled = running
    }

    private fun createLayout(): ScrollView {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val largePadding = (24 * resources.displayMetrics.density).toInt()

        statusText = TextView(this).apply {
            textSize = 18f
            setPadding(padding)
        }

        ipText = TextView(this).apply {
            textSize = 16f
            setPadding(padding)
            setTextColor(0xFFB0BEC5.toInt())
        }

        val hintText = TextView(this).apply {
            text = """
                |使用说明：
                |1. 点击「启动服务」
                |2. 授权屏幕录制权限
                |3. 开启无障碍服务（设置→无障碍→MatisuXCS）
                |4. 在局域网任意设备浏览器打开上方地址
                |5. 开机自启需在系统设置中允许自启动
            """.trimMargin()
            textSize = 13f
            setPadding(padding)
            setTextColor(0xFF90A4AE.toInt())
            setLineSpacing(4f, 1f)
        }

        startBtn = Button(this).apply {
            text = "启动服务"
            setPadding(padding)
            setOnClickListener { startService() }
        }

        stopBtn = Button(this).apply {
            text = "停止服务"
            setPadding(padding)
            setOnClickListener { ControlService.stop(this@MainActivity); updateUI() }
        }

        val accessibilityBtn = Button(this).apply {
            text = "开启无障碍服务"
            setPadding(padding)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(largePadding, largePadding, largePadding, largePadding)
            addView(statusText)
            addView(ipText)
            addView(startBtn)
            addView(stopBtn)
            addView(accessibilityBtn)
            addView(hintText)
        }

        return ScrollView(this).apply { addView(layout) }
    }

    private fun startService() {
        ControlService.start(this)
        ScreenCaptureActivity.request(this)
        updateUI()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (_: Exception) {
            return "0.0.0.0"
        }
    }
}
