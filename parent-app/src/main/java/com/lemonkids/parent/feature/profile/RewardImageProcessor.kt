package com.lemonkids.parent.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun prepareRewardImage(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)
    require(mime == "image/jpeg" || mime == "image/png") { "请选择静态 JPEG 或 PNG 图片" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: error("无法读取图片")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无效或已损坏" }
    val sample = generateSequence(1) { it * 2 }.first {
        bounds.outWidth / it <= 1600 && bounds.outHeight / it <= 1600
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("无法解码图片")
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
    val rotated = if (matrix.isIdentity) decoded else Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height, matrix, true
    )
    val scaled = if (rotated.width > 1600 || rotated.height > 1600) {
        val factor = 1600f / maxOf(rotated.width, rotated.height)
        Bitmap.createScaledBitmap(rotated, (rotated.width * factor).toInt(), (rotated.height * factor).toInt(), true)
    } else rotated
    val output = ByteArrayOutputStream()
    try {
        require(scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "图片处理失败" }
        val bytes = output.toByteArray()
        require(bytes.size in 1..5 * 1024 * 1024) { "图片处理后超过 5 MB，请换一张图片" }
        bytes
    } finally {
        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
    }
}
