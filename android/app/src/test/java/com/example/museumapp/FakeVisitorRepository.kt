package com.example.museumapp

import android.net.Uri
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
import com.example.museumapp.data.model.StudentRegisterRequestDto
import com.example.museumapp.data.model.VisitorMeResponseDto
import com.example.museumapp.data.model.VisitorProfileDto
import com.example.museumapp.data.model.VisitorTokenResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.data.session.VisitorSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeVisitorRepository : VisitorRepositoryContract {
    val sessionState = MutableStateFlow(VisitorSession(accountType = "guest", accessToken = "token", displayName = "Maria Santos"))
    val onboardingState = MutableStateFlow(false)
    override val session: Flow<VisitorSession> = sessionState
    override val onboardingCompleted: Flow<Boolean> = onboardingState
    override val backendBaseUrl: String = "http://testserver/"

    var homeResult: RepositoryResult<PublicHomeResponseDto> = RepositoryResult.Success(
        PublicHomeResponseDto(
            latestNews = listOf(NewsDto("news-1", "News", "Summary", "Body")),
            announcements = listOf(AnnouncementDto("ann-1", "Announcement", "Message")),
            featuredArtifacts = listOf(sampleArtifact()),
            museumInformation = MuseumInformationDto(museumName = "PSAU Museum")
        )
    )
    var artifactListResult: RepositoryResult<PublicArtifactListResponseDto> = RepositoryResult.Success(
        PublicArtifactListResponseDto(listOf(sampleArtifact()), 1, 20, 1, 1)
    )
    var artifactDetailsResult: RepositoryResult<PublicArtifactDto> = RepositoryResult.Success(sampleArtifact())
    var articlesResult: RepositoryResult<List<ArticleDto>> = RepositoryResult.Success(listOf(ArticleDto("article-1", "Article", "Summary", "Body", category = "Farm Tools")))
    var museumInfoResult: RepositoryResult<MuseumInformationDto> = RepositoryResult.Success(MuseumInformationDto(museumName = "PSAU Museum"))
    var programsResult: RepositoryResult<List<ProgramDto>> = RepositoryResult.Success(emptyList())
    var healthResult: RepositoryResult<HealthResponse> = RepositoryResult.Success(HealthResponse("healthy", "connected", "available"))
    var aiHealthResult: RepositoryResult<AiHealthResponse> = RepositoryResult.Success(
        AiHealthResponse(
            status = "healthy",
            aiEnabled = true,
            openclip = "loaded",
            modelName = "ViT-B-32",
            pretrained = "laion2b_s34b_b79k",
            device = "cpu",
            embeddingDimension = 512,
            qdrant = "connected",
            collection = "artifact_images",
            collectionStatus = "ready",
            indexedVectors = 5
        )
    )
    var tokenResponse = VisitorTokenResponseDto(
        accessToken = "visitor-token",
        tokenType = "bearer",
        expiresIn = 3600,
        accountType = "guest",
        profile = VisitorProfileDto(id = "guest-1", firstName = "Maria", lastName = "Santos", displayName = "Maria Santos", role = "guest")
    )
    var logoutCalled = false
    var onboardingCompletedValue: Boolean? = null

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboardingCompletedValue = completed
        onboardingState.value = completed
    }

    override suspend fun checkHealth(): RepositoryResult<HealthResponse> = healthResult
    override suspend fun createGuestSession(request: GuestSessionRequestDto): RepositoryResult<VisitorTokenResponseDto> = RepositoryResult.Success(tokenResponse)
    override suspend fun registerStudent(request: StudentRegisterRequestDto): RepositoryResult<VisitorTokenResponseDto> = RepositoryResult.Success(tokenResponse.copy(accountType = "student"))
    override suspend fun loginStudent(identifier: String, password: String): RepositoryResult<VisitorTokenResponseDto> = RepositoryResult.Success(tokenResponse.copy(accountType = "student"))
    override suspend fun visitorMe(): RepositoryResult<VisitorMeResponseDto> = RepositoryResult.Success(VisitorMeResponseDto(tokenResponse.accountType, tokenResponse.profile))
    override suspend fun logout() {
        logoutCalled = true
        sessionState.value = VisitorSession()
    }
    override suspend fun publicHome(): RepositoryResult<PublicHomeResponseDto> = homeResult
    override suspend fun news(): RepositoryResult<List<NewsDto>> = RepositoryResult.Success(emptyList())
    override suspend fun newsDetails(newsId: String): RepositoryResult<NewsDto> = RepositoryResult.Error("unused")
    override suspend fun announcements(): RepositoryResult<List<AnnouncementDto>> = RepositoryResult.Success(emptyList())
    override suspend fun articles(search: String?, category: String?): RepositoryResult<List<ArticleDto>> = articlesResult
    override suspend fun articleDetails(articleId: String): RepositoryResult<ArticleDto> = RepositoryResult.Error("unused")
    override suspend fun museumInformation(): RepositoryResult<MuseumInformationDto> = museumInfoResult
    override suspend fun programs(): RepositoryResult<List<ProgramDto>> = programsResult
    override suspend fun visitorArtifacts(page: Int, pageSize: Int, search: String?, category: String?, sort: String): RepositoryResult<PublicArtifactListResponseDto> = artifactListResult
    override suspend fun visitorArtifactDetails(artifactId: String): RepositoryResult<PublicArtifactDto> = artifactDetailsResult
    override suspend fun aiHealth(): RepositoryResult<AiHealthResponse> = aiHealthResult
    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> = RepositoryResult.Error("unused")
    override suspend fun recognizeArtifactFile(image: File, limit: Int?): RepositoryResult<RecognitionResponseDto> = RepositoryResult.Error("unused")

    companion object {
        fun sampleArtifact(): PublicArtifactDto = PublicArtifactDto(
            id = "artifact-1",
            artifactCode = "ART-1",
            name = "Wooden Plow",
            description = "A traditional farming tool.",
            category = "Farm Tools"
        )
    }
}
