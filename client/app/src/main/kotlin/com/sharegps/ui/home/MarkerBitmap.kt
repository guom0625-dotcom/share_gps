package com.sharegps.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

private val COLORS = listOf(
    0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFE53935.toInt(),
    0xFF8E24AA.toInt(), 0xFFF4511E.toInt(), 0xFF00ACC1.toInt(),
)

private fun nameColor(name: String): Int = COLORS[name.hashCode().and(0x7FFFFFFF) % COLORS.size]

fun createInitialMarker(name: String, sizePx: Int = 96): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = sizePx / 2f

    paint.color = nameColor(name)
    canvas.drawCircle(r, r, r, paint)

    paint.color = Color.WHITE
    paint.textSize = sizePx * 0.42f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val textY = r - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(name.take(1), r, textY, paint)

    return bmp
}

fun createPhotoMarker(bytes: ByteArray, sizePx: Int = 96): Bitmap {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return createInitialMarker("?", sizePx)

    val side = minOf(src.width, src.height)
    val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(cropped, sizePx, sizePx, true)

    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    return bmp
}
