package com.example.museumapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.museumapp.data.api.AdminApiService
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactListResponse
import com.example.museumapp.data.model.LoginRequest
import com.example.museumapp.data.model.PrimaryImageRequest
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

class AdminRepository(
    private val api: AdminApiService,
    private val sessionManager: SessionManager,
    private val context: Context
) {
    val session: Flow<AdminSession> = sessionManager.session

    suspend fun login(email: String, password: String): RepositoryResult<UserDto> = safeApiCall {
        val response = api.login(LoginRequest(email.trim(), password))
        sessionManager.saveSession(response)
        response.user
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }

    suspend fun currentAdmin(): RepositoryResult<UserDto> = safeApiCall {
        api.currentAdmin()
    }

    suspend fun listArtifacts(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        sort: String
    ): RepositoryResult<ArtifactListResponse> = safeApiCall {
        api.listArtifacts(page, pageSize, search?.takeIf { it.isNotBlank() }, category?.takeIf { it.isNotBlank() }, sort)
    }

    suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.getArtifact(artifactId)
    }

    suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> = safeApiCall {
        api.createArtifact(form.toCreateParts(), imageParts(images))
    }

    suspend fun updateArtifact(
        artifactId: String,
        form: ArtifactFormData,
        images: List<Uri>
    ): RepositoryResult<ArtifactDto> = safeApiCall {
        api.updateArtifact(artifactId, form.toUpdateParts(), imageParts(images))
    }

    suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto> = safeApiCall {
        api.addImages(artifactId, imageParts(images))
    }

    suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.removeImage(artifactId, imageName)
    }

    suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.setPrimaryImage(artifactId, PrimaryImageRequest(imagePath))
    }

    suspend fun deleteArtifact(artifactId: String): RepositoryResult<String> = safeApiCall {
        api.deleteArtifact(artifactId).message
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): RepositoryResult<T> {
        return try {
            RepositoryResult.Success(block())
        } catch (exception: HttpException) {
            if (exception.code() == 401) {
                sessionManager.clearSession()
            }
            RepositoryResult.Error(exception.toUserMessage())
        } catch (exception: IOException) {
            RepositoryResult.Error("Could not connect to the backend. Check that FastAPI is running and reachable.")
        } catch (exception: IllegalArgumentException) {
            RepositoryResult.Error(exception.message ?: "The request could not be prepared.")
        }
    }

    private fun HttpException.toUserMessage(): String {
        val fallback = when (code()) {
            400 -> "The request could not be completed."
            401 -> "Your admin session has expired. Please log in again."
            403 -> "This account cannot access admin artifact management."
            404 -> "The requested artifact was not found."
            409 -> "An artifact with this code already exists."
            413 -> "One of the selected images is too large."
            415 -> "Only JPEG, PNG, and WEBP images can be uploaded."
            422 -> "Please check the form values and try again."
            else -> "The server could not complete the request."
        }
        val body = response()?.errorBody()?.string().orEmpty()
        return runCatching {
            JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }

    private fun ArtifactFormData.toCreateParts(): Map<String, RequestBody> {
        return buildMap {
            put("artifact_code", artifactCode.asTextPart())
            put("name", name.asTextPart())
            put("description", description.asTextPart())
            put("category", category.asTextPart())
            putOptional("origin", origin)
            putOptional("historical_period", historicalPeriod)
            putOptional("material", material)
            putOptional("dimensions", dimensions)
            putOptional("condition", condition)
        }
    }

    private fun ArtifactFormData.toUpdateParts(): Map<String, RequestBody> {
        return buildMap {
            put("artifact_code", artifactCode.asTextPart())
            put("name", name.asTextPart())
            put("description", description.asTextPart())
            put("category", category.asTextPart())
            putOptional("origin", origin)
            putOptional("historical_period", historicalPeriod)
            putOptional("material", material)
            putOptional("dimensions", dimensions)
            putOptional("condition", condition)
            if (removeImagePaths.isNotEmpty()) {
                put("remove_image_paths", removeImagePaths.joinToString(",").asTextPart())
            }
            put("replace_images", replaceImages.toString().asTextPart())
            primaryImagePath?.let { put("primary_image_path", it.asTextPart()) }
        }
    }

    private fun MutableMap<String, RequestBody>.putOptional(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            put(key, value.asTextPart())
        }
    }

    private fun String.asTextPart(): RequestBody = toRequestBody("text/plain".toMediaTypeOrNull())

    private fun imageParts(uris: List<Uri>): List<MultipartBody.Part> {
        return uris.map { uri ->
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            val extension = when (mimeType) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                else -> ".jpg"
            }
            val filename = displayName ?: "selected-${UUID.randomUUID()}$extension"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read a selected image.")
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("images", filename, body)
        }
    }
}
