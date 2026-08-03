package com.example.museumapp

import android.net.Uri
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexAllResponse
import com.example.museumapp.data.model.AiIndexResultResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactListResponse
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.ArtifactFormData
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.session.AdminSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAdminRepository : AdminRepositoryContract {
    val sessionState = MutableStateFlow(
        AdminSession(adminEmail = "admin@example.com", adminName = "Museum Admin", role = "admin", accessToken = "token")
    )
    override val session: Flow<AdminSession> = sessionState
    override val backendBaseUrl: String = "http://testserver/"

    var healthResult: RepositoryResult<HealthResponse> = RepositoryResult.Success(
        HealthResponse(status = "healthy", database = "connected", uploadsDirectory = "available")
    )
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
            indexedVectors = 2
        )
    )
    var warmupResult: RepositoryResult<AiWarmupResponse> = RepositoryResult.Success(
        AiWarmupResponse(
            state = "loaded",
            message = "OpenCLIP is ready.",
            modelName = "ViT-B-32",
            pretrained = "laion2b_s34b_b79k",
            device = "cpu",
            embeddingDimension = 512
        )
    )
    var warmupStatusResults: ArrayDeque<RepositoryResult<AiWarmupResponse>> = ArrayDeque()
    var dashboardResult: RepositoryResult<DashboardSummaryResponse> = RepositoryResult.Error("not configured")
    var currentAdminResult: RepositoryResult<UserDto> = RepositoryResult.Success(
        UserDto(id = "admin-id", email = "admin@example.com", fullName = "Museum Admin", role = "admin")
    )
    var recognitionResult: RepositoryResult<RecognitionResponseDto> = RepositoryResult.Error("not configured")
    var indexAllResult: RepositoryResult<AiIndexAllResponse> = RepositoryResult.Success(
        AiIndexAllResponse(0, 0, 0, 0, 0, 0.0)
    )
    var retryFailedResult: RepositoryResult<AiIndexAllResponse> = indexAllResult
    var rebuildResult: RepositoryResult<AiIndexAllResponse> = indexAllResult
    var indexStatusResult: RepositoryResult<AiIndexStatusResponse> = RepositoryResult.Success(
        AiIndexStatusResponse(
            totalArtifacts = 0,
            totalImages = 0,
            indexedArtifacts = 0,
            pendingArtifacts = 0,
            failedArtifacts = 0,
            partialArtifacts = 0,
            notIndexedArtifacts = 0,
            indexedVectors = 0,
            aiEnabled = true,
            openclip = "loaded",
            qdrant = "connected",
            collection = "artifact_images",
            collectionStatus = "ready"
        )
    )

    var recognizedUri: Uri? = null
    var logoutCalled = false
    var warmupCalls = 0
    var warmupStatusCalls = 0
    var rebuildCalls = 0

    override suspend fun checkHealth(): RepositoryResult<HealthResponse> = healthResult
    override suspend fun aiHealth(): RepositoryResult<AiHealthResponse> = aiHealthResult
    override suspend fun warmupAi(): RepositoryResult<AiWarmupResponse> {
        warmupCalls += 1
        return warmupResult
    }
    override suspend fun warmupAiStatus(): RepositoryResult<AiWarmupResponse> {
        warmupStatusCalls += 1
        return warmupStatusResults.removeFirstOrNull() ?: warmupResult
    }
    override suspend fun login(email: String, password: String): RepositoryResult<UserDto> = RepositoryResult.Error("unused")
    override suspend fun logout() {
        logoutCalled = true
    }
    override suspend fun currentAdmin(): RepositoryResult<UserDto> = currentAdminResult
    override suspend fun dashboardSummary(): RepositoryResult<DashboardSummaryResponse> = dashboardResult
    override suspend fun listArtifacts(page: Int, pageSize: Int, search: String?, category: String?, sort: String): RepositoryResult<ArtifactListResponse> = RepositoryResult.Error("unused")
    override suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun updateArtifact(artifactId: String, form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun deleteArtifact(artifactId: String): RepositoryResult<String> = RepositoryResult.Error("unused")
    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> {
        recognizedUri = image
        return recognitionResult
    }
    override suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse> = RepositoryResult.Error("unused")
    override suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse> = indexAllResult
    override suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse> = retryFailedResult
    override suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse> {
        rebuildCalls += 1
        return rebuildResult
    }
    override suspend fun indexStatus(): RepositoryResult<AiIndexStatusResponse> = indexStatusResult
}
