package com.example.museumapp.model3d

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed class Model3DDownloadProgress {
    data class InProgress(val bytesRead: Long, val totalBytes: Long?) : Model3DDownloadProgress() {
        /** Fraction in [0, 1], or null when the server didn't report a Content-Length. */
        val fraction: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    }

    data class Completed(val file: File) : Model3DDownloadProgress()
}

/**
 * Pure, I/O-free decisions about the local 3D model cache. Kept separate from
 * [Model3DCacheRepository] so the decision logic is unit-testable on plain JVM (real temp
 * [File]s, no Android framework / mocking needed) rather than only exercisable through a real
 * download.
 */
object Model3DCacheDecisions {
    /** True when [existingFile] is missing, empty, or does not hash to [expectedSha256]. */
    fun needsDownload(existingFile: File?, expectedSha256: String): Boolean {
        if (existingFile == null || !existingFile.isFile || existingFile.length() <= 0L) return true
        if (expectedSha256.isBlank()) return true
        return !Sha256.of(existingFile).equals(expectedSha256, ignoreCase = true)
    }

    /** The `*.glb` files in [directory] other than [keepFileName] -- stale versions to delete. */
    fun staleVersionFiles(directory: File, keepFileName: String): List<File> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.name.endsWith(GLB_EXTENSION) && file.name != keepFileName }
            ?.toList()
            .orEmpty()
    }

    const val GLB_EXTENSION = ".glb"
}

/**
 * Downloads and verifies the published GLB for one artifact's 3D model, caching it under
 * app-private storage at `filesDir/models3d/<artifactId>/<version>.glb`.
 *
 * The download URL is a fully-qualified, directly-fetchable HTTP(S) URL supplied by the backend
 * (`model_3d_url` on the visitor artifact DTO) -- the same trust model Coil already uses to load
 * `image_urls` directly. It is fetched with a bare [OkHttpClient], deliberately never routed
 * through the authenticated Retrofit `AdminApiService` / `BackendConnectionInterceptor`.
 */
class Model3DCacheRepository(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val rootDir = File(context.filesDir, "models3d")

    fun cacheDirFor(artifactId: String): File = File(rootDir, artifactId)

    fun cachedFile(artifactId: String, version: Int): File = File(cacheDirFor(artifactId), fileName(version))

    /** The already-cached, hash-verified file for this artifact+version, or null if absent/stale. */
    fun existingValidFile(artifactId: String, version: Int, expectedSha256: String): File? {
        val file = cachedFile(artifactId, version)
        return if (!Model3DCacheDecisions.needsDownload(file, expectedSha256)) file else null
    }

    /**
     * Ensures a verified local copy of the model exists, downloading it if needed. Emits progress
     * while downloading and a final [Model3DDownloadProgress.Completed] with the verified file.
     * Throws (surfaced as a flow exception) on network failure or a SHA-256 mismatch; a mismatched
     * partial download is deleted before the exception propagates.
     */
    fun ensureDownloaded(
        artifactId: String,
        version: Int,
        expectedSha256: String,
        downloadUrl: String
    ): Flow<Model3DDownloadProgress> = flow {
        val finalFile = cachedFile(artifactId, version)
        existingValidFile(artifactId, version, expectedSha256)?.let {
            emit(Model3DDownloadProgress.Completed(it))
            return@flow
        }

        val dir = cacheDirFor(artifactId)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw IOException("Could not create the local 3D model cache folder.")
        }
        val partFile = File(dir, "${fileName(version)}.part")
        partFile.delete()

        val request = Request.Builder().url(downloadUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with status ${response.code}.")
            }
            val body = response.body ?: throw IOException("The server returned an empty response.")
            val totalBytes = body.contentLength().takeIf { it >= 0 }
            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        emit(Model3DDownloadProgress.InProgress(bytesRead, totalBytes))
                    }
                }
            }
        }

        if (Model3DCacheDecisions.needsDownload(partFile, expectedSha256)) {
            partFile.delete()
            throw IOException("The downloaded 3D model failed integrity verification.")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }

        Model3DCacheDecisions.staleVersionFiles(dir, keepFileName = finalFile.name).forEach { it.delete() }

        emit(Model3DDownloadProgress.Completed(finalFile))
    }.flowOn(Dispatchers.IO)

    private fun fileName(version: Int) = "$version${Model3DCacheDecisions.GLB_EXTENSION}"

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 8 * 1024
    }
}
