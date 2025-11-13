package com.roadguardian.auto.utils

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object ImageUtils {
    fun imageToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
