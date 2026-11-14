package com.example.smartalbum.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object BitmapLoader {

    suspend fun loadThumbnail(context: Context, uri: Uri, maxSide: Int = 256): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val boundOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundOpts)
                }
                val w = boundOpts.outWidth
                val h = boundOpts.outHeight
                if (w <= 0 || h <= 0) return@withContext null
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = computeInSampleSize(w, h, maxSide)
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var longest = max(width, height)
        var inSample = 1
        while (longest / inSample > maxSide) {
            inSample *= 2
        }
        return inSample.coerceAtLeast(1)
    }
}
