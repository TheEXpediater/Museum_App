package com.example.museumapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.api.AdminApiService
import com.example.museumapp.data.api.NetworkErrorMessages
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexAllResponse
import com.example.museumapp.data.model.AiIndexResultResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.model.ArtifactCategoryCreateRequest
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCategoryUpdateRequest
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactListResponse
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.LoginRequest
import com.example.museumapp.data.model.PrimaryImageRequest
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID
import okio.BufferedSink
import okio.source

interface RecognitionRepositoryContract {
    suspend fun aiHealth(): RepositoryResult<AiHealthResponse>
    suspend fun recognizeArtifact(image: Uri, limit: Int? = null): RepositoryResult<RecognitionResponseDto>
    suspend fun recognizeArtifactFile(image: File, limit: Int? = null): RepositoryResult<RecognitionResponseDto>
}

interface AdminRepositoryContract : RecognitionRepositoryContract {
    val session: Flow<AdminSession>
    val backendBaseUrl: String

    suspend fun checkHealth(): RepositoryResult<HealthResponse>
    suspend fun warmupAi(): RepositoryResult<AiWarmupResponse>
    suspend fun warmupAiStatus(): RepositoryResult<AiWarmupResponse>
    suspend fun login(email: String, password: String): RepositoryResult<UserDto>
    suspend fun logout()
    suspend fun currentAdmin(): RepositoryResult<UserDto>
    suspend fun dashboardSummary(): RepositoryResult<DashboardSummaryResponse>
    suspend fun listArtifacts(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        sort: String,
        status: String?
    ): RepositoryResult<ArtifactListResponse>
    suspend fun listCategories(): RepositoryResult<List<ArtifactCategoryDto>>
    suspend fun createCategory(name: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun renameCategory(categoryId: String, name: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun deactivateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto>
    suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun updateArtifact(artifactId: String, form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto>
    suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto>
    suspend fun deleteArtifact(artifactId: String): RepositoryResult<String>
    suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse>
    suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse>
    suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse>
    suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse>
    suspend fun indexStatus(): RepositoryResult<AiIndexStatusResponse>
}

class AdminRepository(
    private val api: AdminApiService,
    private val sessionManager: SessionManager,
    private val context: Context
) : AdminRepositoryContract {
    override val session: Flow<AdminSession> = sessionManager.session
    override val backendBaseUrl: String = BuildConfig.API_BASE_URL

    override suspend fun checkHealth(): RepositoryResult<HealthResponse> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.health()
    }

    override suspend fun aiHealth(): RepositoryResult<AiHealthResponse> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.aiHealth()
    }

    override suspend fun warmupAi(): RepositoryResult<AiWarmupResponse> = safeApiCall {
        api.warmupAi()
    }

    override suspend fun warmupAiStatus(): RepositoryResult<AiWarmupResponse> = safeApiCall {
        api.warmupAiStatus()
    }

    override suspend fun login(email: String, password: String): RepositoryResult<UserDto> = safeApiCall(
        clearSessionOnUnauthorized = false,
        unauthorizedMessage = "Invalid email or password."
    ) {
        val response = api.login(LoginRequest(email.trim(), password))
        sessionManager.saveSession(response)
        response.user
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    override suspend fun currentAdmin(): RepositoryResult<UserDto> = safeApiCall {
        api.currentAdmin()
    }

    override suspend fun dashboardSummary(): RepositoryResult<DashboardSummaryResponse> = safeApiCall {
        api.dashboardSummary()
    }

    override suspend fun listArtifacts(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        sort: String,
        status: String?
    ): RepositoryResult<ArtifactListResponse> = safeApiCall {
        api.listArtifacts(page, pageSize, search?.takeIf { it.isNotBlank() }, category?.takeIf { it.isNotBlank() }, sort, status?.takeIf { it != "all" })
    }

    override suspend fun listCategories(): RepositoryResult<List<ArtifactCategoryDto>> = safeApiCall {
        api.listArtifactCategories()
    }

    override suspend fun createCategory(name: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.createArtifactCategory(ArtifactCategoryCreateRequest(name.trim()))
    }

    override suspend fun renameCategory(categoryId: String, name: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.updateArtifactCategory(categoryId, ArtifactCategoryUpdateRequest(name = name.trim()))
    }

    override suspend fun deactivateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.deactivateArtifactCategory(categoryId)
    }

    override suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.getArtifact(artifactId)
    }

    override suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> = safeApiCall {
        api.createArtifact(form.toCreateParts(), imageParts(images))
    }

    override suspend fun updateArtifact(
        artifactId: String,
        form: ArtifactFormData,
        images: List<Uri>
    ): RepositoryResult<ArtifactDto> = safeApiCall {
        api.updateArtifact(artifactId, form.toUpdateParts(), imageParts(images))
    }

    override suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto> = safeApiCall {
        api.addImages(artifactId, imageParts(images))
    }

    override suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.removeImage(artifactId, imageName)
    }

    override suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.setPrimaryImage(artifactId, PrimaryImageRequest(imagePath))
    }

    override suspend fun deleteArtifact(artifactId: String): RepositoryResult<String> = safeApiCall {
        api.deleteArtifact(artifactId).message
    }

    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(singleImagePart("image", image), limit)
    }

    override suspend fun recognizeArtifactFile(image: File, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(fileImagePart("image", image), limit)
    }

    override suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse> = safeApiCall {
        api.indexArtifact(artifactId)
    }

    override suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse> = safeApiCall {
        api.indexAllArtifacts()
    }

    override suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse> = safeApiCall {
        api.retryFailedIndexes()
    }

    override suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse> = safeApiCall {
        api.rebuildArtifactIndex()
    }

    override suspend fun indexStatus(): RepositoryResult<AiIndexStatusResponse> = safeApiCall {
        api.indexStatus()
    }

    private suspend fun <T> safeApiCall(
        clearSessionOnUnauthorized: Boolean = true,
        unauthorizedMessage: String = "Your session has expired. Please log in again.",
        block: suspend () -> T
    ): RepositoryResult<T> {
        return try {
            RepositoryResult.Success(block())
        } catch (exception: HttpException) {
            if (exception.code() == 401 && clearSessionOnUnauthorized) {
                sessionManager.clearSession()
            }
            RepositoryResult.Error(exception.toUserMessage(unauthorizedMessage))
        } catch (exception: IOException) {
            RepositoryResult.Error(NetworkErrorMessages.from(exception))
        } catch (exception: IllegalArgumentException) {
            RepositoryResult.Error(exception.message ?: "The request could not be prepared.")
        }
    }

    private fun HttpException.toUserMessage(unauthorizedMessage: String): String {
        val fallback = when (code()) {
            400 -> "The request could not be completed."
            401 -> unauthorizedMessage
            403 -> "This account does not have administrator access."
            404 -> "The requested artifact was not found."
            409 -> "An artifact with this code already exists."
            413 -> "One of the selected images is too large."
            415 -> "Only JPEG, PNG, and WEBP images can be uploaded."
            422 -> "Please check the form values and try again."
            503 -> "AI services are temporarily unavailable. Check OpenCLIP and Qdrant status."
            500 -> "The backend encountered an internal error. Try again after checking the server logs."
            else -> "The server could not complete the request."
        }
        if (code() == 401 || code() == 403) return fallback

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
            put("status", status.asTextPart())
            put("custom_fields", customFieldsJson().asTextPart())
            putOptional("origin", origin)
            putOptional("historical_period", historicalPeriod)
            putOptional("material", material)
            putOptional("dimensions", dimensions)
            putOptional("condition", condition)
            primaryImageIndex?.let { put("primary_image_index", it.toString().asTextPart()) }
            primaryImagePath?.let { put("primary_image_path", it.asTextPart()) }
        }
    }

    private fun ArtifactFormData.toUpdateParts(): Map<String, RequestBody> {
        return buildMap {
            put("artifact_code", artifactCode.asTextPart())
            put("name", name.asTextPart())
            put("description", description.asTextPart())
            put("category", category.asTextPart())
            put("status", status.asTextPart())
            put("custom_fields", customFieldsJson().asTextPart())
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
            primaryImageIndex?.let { put("primary_image_index", it.toString().asTextPart()) }
        }
    }

    private fun ArtifactFormData.customFieldsJson(): String {
        val fields = JSONArray()
        customFields.forEach { field ->
            fields.put(
                JSONObject()
                    .put("id", field.id)
                    .put("label", field.label)
                    .put("value", field.value)
                    .put("unit", field.unit)
                    .put("type", field.type)
            )
        }
        return fields.toString()
    }

    private fun MutableMap<String, RequestBody>.putOptional(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            put(key, value.asTextPart())
        }
    }

    private fun String.asTextPart(): RequestBody = toRequestBody("text/plain".toMediaTypeOrNull())

    private fun imageParts(uris: List<Uri>): List<MultipartBody.Part> {
        return uris.map { uri -> singleImagePart("images", uri) }
    }

    private fun singleImagePart(partName: String, uri: Uri): MultipartBody.Part {
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
        val body = ContentUriRequestBody(context, uri, mimeType)
        return MultipartBody.Part.createFormData(partName, filename, body)
    }

    private fun fileImagePart(partName: String, file: File): MultipartBody.Part {
        if (!file.isFile || file.length() <= 0L) {
            throw IllegalArgumentException("The captured image could not be processed.")
        }
        val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, body)
    }
}

private class ContentUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mimeType: String
) : RequestBody() {
    override fun contentType() = mimeType.toMediaTypeOrNull()

    override fun contentLength(): Long {
        val resolver = context.contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
            }
            ?: -1L
    }

    override fun writeTo(sink: BufferedSink) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.source().use { source -> sink.writeAll(source) }
        } ?: throw IOException("Could not read a selected image.")
    }
}
