package com.matisu.xcs.server

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

class ScreenCapturer(
    private val metrics: DisplayMetrics,
    private val projection: MediaProjection
) : AutoCloseable {

    private val width = metrics.widthPixels
    private val height = metrics.heightPixels
    private val density = metrics.densityDpi

    private val handlerThread = HandlerThread("ScreenCapturer")
    private val handler: Handler

    private val imageReader: ImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private var virtualDisplay: VirtualDisplay? = null
    private var latestBitmap: Bitmap? = null
    private var isCapturing = false

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        virtualDisplay = projection.createVirtualDisplay(
            "ScreenCapturer",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                try {
                    latestBitmap = imageToBitmap(image)
                } finally {
                    image.close()
                }
            }
        }, handler)

        isCapturing = true
    }

    fun capture(format: String = "jpeg", quality: Int = 80): ByteArray? {
        if (!isCapturing) return null

        val bitmap = latestBitmap ?: return null
        val stream = ByteArrayOutputStream()

        val formatEnum = when (format.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }

        val q = if (format.lowercase() == "png") 100 else min(quality.coerceIn(1, 100), 100)
        bitmap.compress(formatEnum, q, stream)
        return stream.toByteArray()
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    override fun close() {
        isCapturing = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader.close()
        projection.stop()
        handlerThread.quitSafely()
    }
}
