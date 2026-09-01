package com.example.museumapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.api.AdminApiService
import com.example.museumapp.data.api.NetworkErrorMessages
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexAllResponse
import com.example.museumapp.data.model.AiIndexResultResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiLibraryFeedResponse
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed class ArtifactMutationEvent {
    data class Created(val artifactId: String, val status: String) : ArtifactMutationEvent()
    data class Updated(val artifactId: String, val status: String) : ArtifactMutationEvent()
    data class Published(val artifactId: String) : ArtifactMutationEvent()
    data class Deleted(val artifactId: String) : ArtifactMutationEvent()
    object AiLibraryUpdated : ArtifactMutationEvent()
}

interface AdminRepositoryContract : RecognitionRepositoryContract {
    val session: Flow<AdminSession>
    val backendBaseUrl: String
    val artifactMutations: Flow<ArtifactMutationEvent>

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
    suspend fun listCategories(includeInactive: Boolean = false): RepositoryResult<List<ArtifactCategoryDto>>
    suspend fun createCategory(name: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun renameCategory(categoryId: String, name: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun activateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun deactivateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto>
    suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto>
    suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun updateArtifact(artifactId: String, form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto>
    suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto>
    suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto>
    suspend fun deleteArtifact(artifactId: String): RepositoryResult<String>
    suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse>
    suspend fun feedPendingAiLibrary(): RepositoryResult<AiLibraryFeedResponse>
    suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse>
    suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse>
    suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse>
    suspend fun indexStatus(): RepositoryResult<AiIndexStatusResponse>
}

class AdminRepository(
    private val api: AdminApiService,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val backendConnectionManager: BackendConnectionManager
) : AdminRepositoryContract {
    override val session: Flow<AdminSession> = sessionManager.session
    override val backendBaseUrl: String
        get() = backendConnectionManager.activeHost?.let { host -> "http://$host:${backendConnectionManager.activePort}/" }
            ?: BuildConfig.API_BASE_URL
    private val _artifactMutations = MutableSharedFlow<ArtifactMutationEvent>(extraBufferCapacity = 16)
    override val artifactMutations: Flow<ArtifactMutationEvent> = _artifactMutations.asSharedFlow()

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

    override suspend fun listCategories(includeInactive: Boolean): RepositoryResult<List<ArtifactCategoryDto>> = safeApiCall {
        api.listArtifactCategories(includeInactive)
    }

    override suspend fun createCategory(name: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.createArtifactCategory(ArtifactCategoryCreateRequest(name.trim()))
    }

    override suspend fun renameCategory(categoryId: String, name: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.updateArtifactCategory(categoryId, ArtifactCategoryUpdateRequest(name = name.trim()))
    }

    override suspend fun activateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.updateArtifactCategory(categoryId, ArtifactCategoryUpdateRequest(isActive = true))
    }

    override suspend fun deactivateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto> = safeApiCall {
        api.deactivateArtifactCategory(categoryId)
    }

    override suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto> = safeApiCall {
        api.getArtifact(artifactId)
    }

    override suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> {
        val result = safeApiCall {
            api.createArtifact(form.toCreateParts(), imageParts(images))
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.Created(result.data.id, result.data.status))
        }
        return result
    }

    override suspend fun updateArtifact(
        artifactId: String,
        form: ArtifactFormData,
        images: List<Uri>
    ): RepositoryResult<ArtifactDto> {
        val result = safeApiCall {
            api.updateArtifact(artifactId, form.toUpdateParts(), imageParts(images))
        }
        if (result is RepositoryResult.Success) {
            if (form.status == "published" && result.data.status == "published") {
                notifyArtifactMutation(ArtifactMutationEvent.Published(artifactId))
            } else {
                notifyArtifactMutation(ArtifactMutationEvent.Updated(artifactId, result.data.status))
            }
        }
        return result
    }

    override suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto> {
        val result = safeApiCall {
            api.addImages(artifactId, imageParts(images))
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.Updated(artifactId, result.data.status))
        }
        return result
    }

    override suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto> {
        val result = safeApiCall {
            api.removeImage(artifactId, imageName)
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.Updated(artifactId, result.data.status))
        }
        return result
    }

    override suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto> {
        val result = safeApiCall {
            api.setPrimaryImage(artifactId, PrimaryImageRequest(imagePath))
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.Updated(artifactId, result.data.status))
        }
        return result
    }

    override suspend fun deleteArtifact(artifactId: String): RepositoryResult<String> {
        val result = safeApiCall {
            api.deleteArtifact(artifactId).message
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.Deleted(artifactId))
        }
        return result
    }

    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(singleImagePart("image", image), limit)
    }

    override suspend fun recognizeArtifactFile(image: File, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(fileImagePart("image", image), limit)
    }

    override suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse> {
        val result = safeApiCall {
            api.indexArtifact(artifactId)
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return result
    }

    override suspend fun feedPendingAiLibrary(): RepositoryResult<AiLibraryFeedResponse> {
        val result = safeApiCall {
            api.feedPendingAiLibrary()
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return result
    }

    override suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse> {
        val result = safeApiCall {
            api.indexAllArtifacts()
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return result
    }

    override suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse> {
        val result = safeApiCall {
            api.retryFailedIndexes()
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return result
    }

    override suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse> {
        val result = safeApiCall {
            api.rebuildArtifactIndex()
        }
        if (result is RepositoryResult.Success) {
            notifyArtifactMutation(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return result
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
            validationMessageFromDetail(JSONObject(body).opt("detail"))
        }.getOrNull() ?: fallback
    }

    private fun validationMessageFromDetail(detail: Any?): String? {
        return when (detail) {
            is String -> friendlyValidationMessage(detail)
            is JSONArray -> {
                (0 until detail.length()).asSequence()
                    .mapNotNull { detail.optJSONObject(it) }
                    .mapNotNull { validationObjectMessage(it) }
                    .firstOrNull()
            }
            else -> null
        }
    }

    private fun validationObjectMessage(item: JSONObject): String? {
        val message = item.optString("msg").takeIf { it.isNotBlank() } ?: return null
        val field = item.optJSONArray("loc")?.let { loc ->
            (loc.length() - 1 downTo 0)
                .asSequence()
                .map { loc.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }
        if (message.contains("at most", ignoreCase = true) || message.contains("characters", ignoreCase = true)) {
            return "${fieldDisplayName(field)} contains more text than the supported limit."
        }
        return friendlyValidationMessage(message)
    }

    private fun friendlyValidationMessage(message: String): String? {
        val cleaned = message.trim().takeIf { it.isNotBlank() } ?: return null
        val firstWord = cleaned.substringBefore(" ")
        if (cleaned.contains("characters or fewer", ignoreCase = true)) {
            return "${fieldDisplayName(firstWord)} contains more text than the supported limit."
        }
        return cleaned
    }

    private fun fieldDisplayName(raw: String?): String {
        return when (raw) {
            "artifact_code" -> "Artifact code"
            "name" -> "Artifact name"
            "description" -> "Description"
            "category" -> "Category"
            "origin" -> "Origin"
            "historical_period" -> "Historical period"
            "material" -> "Material"
            "dimensions" -> "Dimensions"
            "condition" -> "Condition"
            "metadata_sections" -> "Metadata sections"
            "visitor_gallery_image_paths" -> "Visitor gallery images"
            else -> raw.orEmpty().replace("_", " ").replaceFirstChar { it.uppercase() }.ifBlank { "This field" }
        }
    }

    private fun ArtifactFormData.toCreateParts(): Map<String, RequestBody> {
        return buildMap {
            put("artifact_code", artifactCode.asTextPart())
            put("name", name.asTextPart())
            put("description", description.asTextPart())
            put("category", category.asTextPart())
            put("status", status.asTextPart())
            put("custom_fields", customFieldsJson().asTextPart())
            put("metadata_sections", metadataSectionsJson().asTextPart())
            put("visitor_gallery_image_paths", visitorGalleryImagePathsJson().asTextPart())
            put("visitor_gallery_configured", visitorGalleryConfigured.toString().asTextPart())
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
            put("metadata_sections", metadataSectionsJson().asTextPart())
            put("visitor_gallery_image_paths", visitorGalleryImagePathsJson().asTextPart())
            put("visitor_gallery_configured", visitorGalleryConfigured.toString().asTextPart())
            putNullable("origin", origin)
            putNullable("historical_period", historicalPeriod)
            putNullable("material", material)
            putNullable("dimensions", dimensions)
            putNullable("condition", condition)
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

    private fun ArtifactFormData.metadataSectionsJson(): String {
        val sections = JSONArray()
        metadataSections.forEach { section ->
            val fields = JSONArray()
            section.fields.forEach { field ->
                fields.put(
                    JSONObject()
                        .put("id", field.id)
                        .put("label", field.label)
                        .put("value", field.value)
                        .put("type", field.type)
                        .put("unit", field.unit)
                        .put("order", field.order)
                )
            }
            sections.put(
                JSONObject()
                    .put("id", section.id)
                    .put("title", section.title)
                    .put("order", section.order)
                    .put("fields", fields)
            )
        }
        return sections.toString()
    }

    private fun ArtifactFormData.visitorGalleryImagePathsJson(): String {
        val paths = JSONArray()
        visitorGalleryImagePaths.forEach { path -> paths.put(path) }
        return paths.toString()
    }

    private fun MutableMap<String, RequestBody>.putOptional(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            put(key, value.asTextPart())
        }
    }

    private fun MutableMap<String, RequestBody>.putNullable(key: String, value: String?) {
        put(key, value.orEmpty().asTextPart())
    }

    private fun String.asTextPart(): RequestBody = toRequestBody("text/plain".toMediaTypeOrNull())

    private fun notifyArtifactMutation(event: ArtifactMutationEvent) {
        _artifactMutations.tryEmit(event)
    }

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
