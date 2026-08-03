package com.example.museumapp.ui.admin.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object RecognitionImagePreparer {
    private const val MAX_LONG_EDGE = 2048
    private const val JPEG_QUALITY = 90

    fun createRawCaptureFile(context: Context): File {
        val directory = File(context.cacheDir, "recognition-captures").apply { mkdirs() }
        return File.createTempFile("recognition-raw-", ".jpg", directory)
    }

    fun prepareCapturedImage(context: Context, rawFile: File): File {
        if (!rawFile.isFile || rawFile.length() <= 0L) {
            throw IllegalArgumentException("The captured image could not be processed.")
        }

        val rotation = rawFile.rotationDegrees()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(rawFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            rawFile.deleteFileQuietly()
            throw IllegalArgumentException("The captured image could not be processed.")
        }

        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (rotation == 0 && longEdge <= MAX_LONG_EDGE) {
            return rawFile
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(longEdge)
        }
        val decoded = BitmapFactory.decodeFile(rawFile.absolutePath, decodeOptions)
            ?: run {
                rawFile.deleteFileQuietly()
                throw IllegalArgumentException("The captured image could not be processed.")
            }
        val resized = decoded.resizeIfNeeded()
        val oriented = resized.rotateIfNeeded(rotation)
        val prepared = createPreparedFile(context)

        FileOutputStream(prepared).use { output ->
            if (!oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                prepared.deleteFileQuietly()
                rawFile.deleteFileQuietly()
                recycleIntermediates(decoded, resized, oriented)
                throw IllegalArgumentException("The captured image could not be processed.")
            }
        }

        recycleIntermediates(decoded, resized, oriented)
        rawFile.deleteFileQuietly()
        return prepared
    }

    fun deleteQuietly(file: File?) {
        file?.deleteFileQuietly()
    }

    private fun createPreparedFile(context: Context): File {
        val directory = File(context.cacheDir, "recognition-captures").apply { mkdirs() }
        return File.createTempFile("recognition-", ".jpg", directory)
    }

    private fun File.rotationDegrees(): Int {
        return runCatching {
            when (ExifInterface(absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun sampleSizeFor(longEdge: Int): Int {
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= MAX_LONG_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.resizeIfNeeded(): Bitmap {
        val longEdge = max(width, height)
        if (longEdge <= MAX_LONG_EDGE) return this
        val scale = MAX_LONG_EDGE.toFloat() / longEdge.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun recycleIntermediates(decoded: Bitmap, resized: Bitmap, oriented: Bitmap) {
        if (oriented !== resized) oriented.recycle()
        if (resized !== decoded) resized.recycle()
        decoded.recycle()
    }

    private fun File.deleteFileQuietly() {
        runCatching {
            if (exists()) delete()
        }
    }
}
