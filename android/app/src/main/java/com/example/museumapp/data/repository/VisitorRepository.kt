package com.example.museumapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.api.AdminApiService
import com.example.museumapp.data.api.NetworkErrorMessages
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AnnouncementDto
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.GuestSessionRequestDto
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.MuseumInformationDto
import com.example.museumapp.data.model.NewsDto
import com.example.museumapp.data.model.ProgramDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.model.PublicArtifactListResponseDto
import com.example.museumapp.data.model.PublicHomeResponseDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.model.StudentLoginRequestDto
import com.example.museumapp.data.model.StudentRegisterRequestDto
import com.example.museumapp.data.model.VisitorMeResponseDto
import com.example.museumapp.data.model.VisitorTokenResponseDto
import com.example.museumapp.data.session.SessionManager
import com.example.museumapp.data.session.VisitorSession
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID

interface VisitorRepositoryContract : RecognitionRepositoryContract {
    val session: Flow<VisitorSession>
    val onboardingCompleted: Flow<Boolean>
    val backendBaseUrl: String

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun checkHealth(): RepositoryResult<HealthResponse>
    suspend fun createGuestSession(request: GuestSessionRequestDto): RepositoryResult<VisitorTokenResponseDto>
    suspend fun registerStudent(request: StudentRegisterRequestDto): RepositoryResult<VisitorTokenResponseDto>
    suspend fun loginStudent(identifier: String, password: String): RepositoryResult<VisitorTokenResponseDto>
    suspend fun visitorMe(): RepositoryResult<VisitorMeResponseDto>
    suspend fun logout()
    suspend fun publicHome(): RepositoryResult<PublicHomeResponseDto>
    suspend fun news(): RepositoryResult<List<NewsDto>>
    suspend fun newsDetails(newsId: String): RepositoryResult<NewsDto>
    suspend fun announcements(): RepositoryResult<List<AnnouncementDto>>
    suspend fun articles(search: String? = null, category: String? = null): RepositoryResult<List<ArticleDto>>
    suspend fun articleDetails(articleId: String): RepositoryResult<ArticleDto>
    suspend fun museumInformation(): RepositoryResult<MuseumInformationDto>
    suspend fun programs(): RepositoryResult<List<ProgramDto>>
    suspend fun visitorArtifacts(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        sort: String
    ): RepositoryResult<PublicArtifactListResponseDto>
    suspend fun visitorArtifactDetails(artifactId: String): RepositoryResult<PublicArtifactDto>
}

class VisitorRepository(
    private val api: AdminApiService,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val backendConnectionManager: BackendConnectionManager
) : VisitorRepositoryContract {
    override val session: Flow<VisitorSession> = sessionManager.visitorSession
    override val onboardingCompleted: Flow<Boolean> = sessionManager.onboardingCompleted
    override val backendBaseUrl: String
        get() = backendConnectionManager.activeHost?.let { host -> "http://$host:${backendConnectionManager.activePort}/" }
            ?: BuildConfig.API_BASE_URL

    private var cachedHome: PublicHomeResponseDto? = null
    private var cachedArtifactList: PublicArtifactListResponseDto? = null

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        sessionManager.setOnboardingCompleted(completed)
    }

    override suspend fun checkHealth(): RepositoryResult<HealthResponse> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.health()
    }

    override suspend fun createGuestSession(request: GuestSessionRequestDto): RepositoryResult<VisitorTokenResponseDto> = safeApiCall(
        clearSessionOnUnauthorized = false
    ) {
        val response = api.createGuestSession(request)
        sessionManager.saveVisitorSession(response)
        response
    }

    override suspend fun registerStudent(request: StudentRegisterRequestDto): RepositoryResult<VisitorTokenResponseDto> = safeApiCall(
        clearSessionOnUnauthorized = false
    ) {
        val response = api.registerStudent(request)
        sessionManager.saveVisitorSession(response)
        response
    }

    override suspend fun loginStudent(identifier: String, password: String): RepositoryResult<VisitorTokenResponseDto> = safeApiCall(
        clearSessionOnUnauthorized = false,
        unauthorizedMessage = "Invalid student ID, email, or password."
    ) {
        val response = api.loginStudent(StudentLoginRequestDto(identifier.trim(), password))
        sessionManager.saveVisitorSession(response)
        response
    }

    override suspend fun visitorMe(): RepositoryResult<VisitorMeResponseDto> = safeApiCall {
        api.visitorMe()
    }

    override suspend fun logout() {
        runCatching { api.visitorLogout() }
        sessionManager.clearSession()
    }

    override suspend fun publicHome(): RepositoryResult<PublicHomeResponseDto> {
        return when (val result = safeApiCall(clearSessionOnUnauthorized = false) { api.publicHome() }) {
            is RepositoryResult.Success -> {
                cachedHome = result.data
                result
            }
            is RepositoryResult.Error -> cachedHome?.let { RepositoryResult.Success(it) } ?: result
        }
    }

    override suspend fun news(): RepositoryResult<List<NewsDto>> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.publicNews()
    }

    override suspend fun newsDetails(newsId: String): RepositoryResult<NewsDto> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.publicNewsDetails(newsId)
    }

    override suspend fun announcements(): RepositoryResult<List<AnnouncementDto>> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.publicAnnouncements()
    }

    override suspend fun articles(search: String?, category: String?): RepositoryResult<List<ArticleDto>> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.publicArticles(search?.takeIf { it.isNotBlank() }, category?.takeIf { it.isNotBlank() })
    }

    override suspend fun articleDetails(articleId: String): RepositoryResult<ArticleDto> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.publicArticleDetails(articleId)
    }

    override suspend fun museumInformation(): RepositoryResult<MuseumInformationDto> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.museumInformation()
    }

    override suspend fun programs(): RepositoryResult<List<ProgramDto>> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.programs()
    }

    override suspend fun visitorArtifacts(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        sort: String
    ): RepositoryResult<PublicArtifactListResponseDto> {
        return when (val result = safeApiCall { api.visitorArtifacts(page, pageSize, search?.takeIf { it.isNotBlank() }, category?.takeIf { it.isNotBlank() }, sort) }) {
            is RepositoryResult.Success -> {
                cachedArtifactList = result.data
                result
            }
            is RepositoryResult.Error -> cachedArtifactList?.let { RepositoryResult.Success(it) } ?: result
        }
    }

    override suspend fun visitorArtifactDetails(artifactId: String): RepositoryResult<PublicArtifactDto> = safeApiCall {
        api.visitorArtifactDetails(artifactId)
    }

    override suspend fun aiHealth(): RepositoryResult<AiHealthResponse> = safeApiCall(clearSessionOnUnauthorized = false) {
        api.aiHealth()
    }

    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(singleImagePart("image", image), limit)
    }

    override suspend fun recognizeArtifactFile(image: File, limit: Int?): RepositoryResult<RecognitionResponseDto> = safeApiCall {
        api.recognizeArtifact(fileImagePart("image", image), limit)
    }

    private suspend fun <T> safeApiCall(
        clearSessionOnUnauthorized: Boolean = true,
        unauthorizedMessage: String = "Your session has expired. Please sign in again.",
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
            403 -> "This account cannot open that section."
            404 -> "The requested content was not found."
            409 -> "This account information is already in use."
            413 -> "The selected image is too large."
            415 -> "Only JPEG, PNG, and WEBP images can be uploaded."
            422 -> "Please check the form values and try again."
            503 -> "Artifact scanning is temporarily unavailable."
            else -> "The server could not complete the request."
        }
        if (code() == 401 || code() == 403) return fallback

        val body = response()?.errorBody()?.string().orEmpty()
        return runCatching {
            JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
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
        val filename = displayName ?: "visitor-selected-${UUID.randomUUID()}$extension"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read the selected image.")
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
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
