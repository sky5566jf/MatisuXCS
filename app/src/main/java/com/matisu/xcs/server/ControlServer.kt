package com.matisu.xcs.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.matisu.xcs.service.TouchService
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class ControlServer(
    private val context: Context,
    port: Int = 8888
) : NanoWSD("0.0.0.0", port) {

    private val clients = CopyOnWriteArrayList<WebSocket>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchService: TouchService? = null
    private var screenCapturer: ScreenCapturer? = null
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onScreenCaptureRequest: ((format: String, quality: Int) -> ByteArray?)? = null

    fun setTouchService(service: TouchService?) {
        touchService = service
    }

    fun setScreenCapturer(capturer: ScreenCapturer?) {
        screenCapturer = capturer
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/" || uri == "/index.html" -> serveWebUI()
            uri == "/screenshot" -> handleScreenshot(session)
            uri == "/screenshot/stream" -> handleMJPEGStream(session)
            uri == "/device/info" -> handleDeviceInfo()
            uri == "/ping" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "pong")

            method == Method.POST -> handlePost(session)

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "Not Found"
            )
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        return object : WebSocket(handshake) {
            override fun onOpen() {
                clients.add(this)
            }

            override fun onClose(
                code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean
            ) {
                clients.remove(this)
            }

            override fun onMessage(frame: WebSocketFrame) {
                handleWebSocketMessage(frame.textPayload)
            }

            override fun onPong(pong: WebSocketFrame) {}
            override fun onException(exception: IOException) {
                clients.remove(this)
            }
        }
    }

    private fun handleWebSocketMessage(message: String) {
        try {
            val parts = message.split(" ", limit = 2)
            val cmd = parts[0]
            val params = if (parts.size > 1) parts[1] else ""

            mainHandler.post {
                when (cmd) {
                    "down" -> {
                        val (x, y) = parseXY(params)
                        touchService?.dispatchGesture(x, y, 0L)
                    }

                    "up" -> {
                        // up is handled client-side via tap detection
                    }

                    "move" -> {
                        val (x, y) = parseXY(params)
                        val segments = params.split(",")
                        val duration = segments.getOrNull(2)?.toLongOrNull() ?: 200L
                        touchService?.dispatchSwipe(
                            x, y,
                            segments.getOrNull(3)?.toFloatOrNull()?.toInt() ?: x,
                            segments.getOrNull(4)?.toFloatOrNull()?.toInt() ?: y,
                            duration
                        )
                    }

                    "tap" -> {
                        val (x, y) = parseXY(params)
                        touchService?.dispatchTap(x, y)
                    }

                    "longpress" -> {
                        val (x, y) = parseXY(params)
                        val duration = params.split(",").getOrNull(2)?.toLongOrNull() ?: 800L
                        touchService?.dispatchLongPress(x, y, duration)
                    }

                    "swipe" -> {
                        val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                        if (coords.size >= 4) {
                            val duration = coords.getOrElse(4) { 200L.toInt() }.toLong()
                            touchService?.dispatchSwipe(
                                coords[0], coords[1], coords[2], coords[3], duration
                            )
                        }
                    }

                    "key" -> touchService?.performGlobalAction(params)
                    "text" -> touchService?.inputText(params)
                    "start_stream" -> startStream()
                    "stop_stream" -> stopStream()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun parseXY(params: String): Pair<Int, Int> {
        val parts = params.split(",")
        return Pair(
            parts.getOrNull(0)?.trim()?.toFloatOrNull()?.toInt() ?: 0,
            parts.getOrNull(1)?.trim()?.toFloatOrNull()?.toInt() ?: 0
        )
    }

    private fun serveWebUI(): Response {
        val html = WebUI.getHTML(context)
        return newFixedLengthResponse(
            Response.Status.OK, "text/html; charset=utf-8", ByteArrayInputStream(html.toByteArray()),
            html.toByteArray().size.toLong()
        )
    }

    private fun handleScreenshot(session: IHTTPSession): Response {
        val format = session.parameters["fmt"]?.firstOrNull() ?: "jpeg"
        val quality = session.parameters["q"]?.firstOrNull()?.toIntOrNull() ?: 80
        val data = onScreenCaptureRequest?.invoke(format, quality)

        return if (data != null) {
            val mime = if (format == "png") "image/png" else "image/jpeg"
            newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(data), data.size.toLong())
        } else {
            newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Screen capture not available")
        }
    }

    private fun handleMJPEGStream(session: IHTTPSession): Response {
        return newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=--boundary"
        ) {
            try {
                while (true) {
                    val data = onScreenCaptureRequest?.invoke("jpeg", 60) ?: break
                    it.write("--boundary\r\n".toByteArray())
                    it.write("Content-Type: image/jpeg\r\n".toByteArray())
                    it.write("Content-Length: ${data.size}\r\n\r\n".toByteArray())
                    it.write(data)
                    it.write("\r\n".toByteArray())
                    it.flush()
                    Thread.sleep(50)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun handleDeviceInfo(): Response {
        val info = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${Build.MODEL}\",")
            appendLine("  \"manufacturer\": \"${Build.MANUFACTURER}\",")
            appendLine("  \"sdk\": ${Build.VERSION.SDK_INT},")
            appendLine("  \"release\": \"${Build.VERSION.RELEASE}\",")
            appendLine("  \"brand\": \"${Build.BRAND}\"")
            appendLine("}")
        }
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", info
        )
    }

    private fun handlePost(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)

        return when (session.uri) {
            "/touch/down" -> {
                val x = body["x"]?.toIntOrNull() ?: 0
                val y = body["y"]?.toIntOrNull() ?: 0
                mainHandler.post { touchService?.dispatchGesture(x, y, 0L) }
                ok()
            }

            "/touch/up" -> {
                val x = body["x"]?.toIntOrNull() ?: 0
                val y = body["y"]?.toIntOrNull() ?: 0
                mainHandler.post { touchService?.dispatchGesture(x, y, 1L) }
                ok()
            }

            "/tap" -> {
                val x = body["x"]?.toIntOrNull() ?: 0
                val y = body["y"]?.toIntOrNull() ?: 0
                mainHandler.post { touchService?.dispatchTap(x, y) }
                ok()
            }

            "/longpress" -> {
                val x = body["x"]?.toIntOrNull() ?: 0
                val y = body["y"]?.toIntOrNull() ?: 0
                val duration = body["duration"]?.toLongOrNull() ?: 800L
                mainHandler.post { touchService?.dispatchLongPress(x, y, duration) }
                ok()
            }

            "/swipe" -> {
                val x1 = body["x1"]?.toIntOrNull() ?: 0
                val y1 = body["y1"]?.toIntOrNull() ?: 0
                val x2 = body["x2"]?.toIntOrNull() ?: 0
                val y2 = body["y2"]?.toIntOrNull() ?: 0
                val duration = body["duration"]?.toLongOrNull() ?: 200L
                mainHandler.post { touchService?.dispatchSwipe(x1, y1, x2, y2, duration) }
                ok()
            }

            "/key" -> {
                val key = body["key"] ?: ""
                mainHandler.post { touchService?.performGlobalAction(key) }
                ok()
            }

            "/text" -> {
                val text = body["text"] ?: ""
                mainHandler.post { touchService?.inputText(text) }
                ok()
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun ok(): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")

    private fun startStream() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            while (isActive) {
                val data = onScreenCaptureRequest?.invoke("jpeg", 50) ?: break
                val frame = java.util.Base64.getEncoder().encodeToString(data)
                val msg = "frame $frame"
                clients.forEach { client ->
                    try { client.send(msg) } catch (_: Exception) {}
                }
                delay(33) // ~30fps
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    fun destroy() {
        stopStream()
        scope.cancel()
        stop()
    }
}
