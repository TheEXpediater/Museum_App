package com.example.museumapp

import android.net.Uri
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexAllResponse
import com.example.museumapp.data.model.AiIndexResultResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiLibraryFeedResponse
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactMetadataFieldDto
import com.example.museumapp.data.model.ArtifactMetadataSectionDto
import com.example.museumapp.data.model.ArtifactListResponse
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.ArtifactMutationEvent
import com.example.museumapp.data.repository.ArtifactFormData
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.session.AdminSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class FakeAdminRepository : AdminRepositoryContract {
    val sessionState = MutableStateFlow(
        AdminSession(adminEmail = "admin@example.com", adminName = "Museum Admin", role = "admin", accessToken = "token")
    )
    override val session: Flow<AdminSession> = sessionState
    override val backendBaseUrl: String = "http://testserver/"
    private val artifactMutationEvents = MutableSharedFlow<ArtifactMutationEvent>(extraBufferCapacity = 16)
    override val artifactMutations: Flow<ArtifactMutationEvent> = artifactMutationEvents.asSharedFlow()

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
    var feedAiLibraryResult: RepositoryResult<AiLibraryFeedResponse> = RepositoryResult.Success(
        AiLibraryFeedResponse(0, 0, 0, 0)
    )
    var retryFailedResult: RepositoryResult<AiIndexAllResponse> = indexAllResult
    var rebuildResult: RepositoryResult<AiIndexAllResponse> = indexAllResult
    var categoriesResult: RepositoryResult<List<ArtifactCategoryDto>> = RepositoryResult.Success(emptyList())
    var artifactResult: RepositoryResult<ArtifactDto> = RepositoryResult.Success(sampleArtifact())
    var createArtifactResult: RepositoryResult<ArtifactDto> = RepositoryResult.Success(sampleArtifact(status = "draft"))
    var updateArtifactResult: RepositoryResult<ArtifactDto> = RepositoryResult.Success(sampleArtifact(status = "draft"))
    var addImagesResult: RepositoryResult<ArtifactDto> = RepositoryResult.Success(sampleArtifact(imageCount = 2))
    var setPrimaryImageResult: RepositoryResult<ArtifactDto> = RepositoryResult.Success(sampleArtifact())
    var deleteArtifactResult: RepositoryResult<String> = RepositoryResult.Success("deleted")
    var listArtifactsResult: RepositoryResult<ArtifactListResponse> = RepositoryResult.Success(
        ArtifactListResponse(emptyList(), page = 1, pageSize = 20, totalItems = 0, totalPages = 0)
    )
    var indexArtifactResult: RepositoryResult<AiIndexResultResponse> = RepositoryResult.Success(
        AiIndexResultResponse(
            artifactId = "artifact-1",
            aiIndexStatus = "indexed",
            totalImages = 1,
            indexedImages = 1,
            failedImages = 0,
            skippedImages = 0
        )
    )
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
    var recognizedFile: File? = null
    var logoutCalled = false
    var warmupCalls = 0
    var warmupStatusCalls = 0
    var rebuildCalls = 0
    var createdForm: ArtifactFormData? = null
    var updatedForm: ArtifactFormData? = null
    var createdImages: List<Uri> = emptyList()
    var updatedImages: List<Uri> = emptyList()
    var addedImages: List<Uri> = emptyList()
    var primaryImagePathSet: String? = null
    var indexArtifactCalls = 0
    var feedAiLibraryCalls = 0
    var dashboardCalls = 0

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
    override suspend fun dashboardSummary(): RepositoryResult<DashboardSummaryResponse> {
        dashboardCalls += 1
        return dashboardResult
    }
    var lastListArtifactsStatus: String? = null

    override suspend fun listArtifacts(page: Int, pageSize: Int, search: String?, category: String?, sort: String, status: String?): RepositoryResult<ArtifactListResponse> {
        lastListArtifactsStatus = status
        return listArtifactsResult
    }
    var categoryDeactivateCalls = 0
    var categoryActivateCalls = 0
    var lastCreatedCategoryName: String? = null
    var lastRenamedCategory: Pair<String, String>? = null
    var lastDeactivatedCategoryId: String? = null
    var lastActivatedCategoryId: String? = null
    var lastListCategoriesIncludeInactive: Boolean? = null

    override suspend fun listCategories(includeInactive: Boolean): RepositoryResult<List<ArtifactCategoryDto>> {
        lastListCategoriesIncludeInactive = includeInactive
        return categoriesResult
    }
    override suspend fun createCategory(name: String): RepositoryResult<ArtifactCategoryDto> {
        lastCreatedCategoryName = name
        return RepositoryResult.Success(sampleCategory(id = "category-created", name = name))
    }
    override suspend fun renameCategory(categoryId: String, name: String): RepositoryResult<ArtifactCategoryDto> {
        lastRenamedCategory = categoryId to name
        return RepositoryResult.Success(sampleCategory(id = categoryId, name = name))
    }
    override suspend fun activateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto> {
        categoryActivateCalls += 1
        lastActivatedCategoryId = categoryId
        return RepositoryResult.Success(sampleCategory(id = categoryId, name = "Activated", isActive = true))
    }
    override suspend fun deactivateCategory(categoryId: String): RepositoryResult<ArtifactCategoryDto> {
        categoryDeactivateCalls += 1
        lastDeactivatedCategoryId = categoryId
        return RepositoryResult.Success(sampleCategory(id = categoryId, name = "Deactivated", isActive = false))
    }
    override suspend fun getArtifact(artifactId: String): RepositoryResult<ArtifactDto> = artifactResult
    override suspend fun createArtifact(form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> {
        createdForm = form
        createdImages = images
        val result = createArtifactResult
        if (result is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.Created(result.data.id, result.data.status))
        }
        return createArtifactResult
    }
    override suspend fun updateArtifact(artifactId: String, form: ArtifactFormData, images: List<Uri>): RepositoryResult<ArtifactDto> {
        updatedForm = form
        updatedImages = images
        val result = updateArtifactResult
        if (result is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.Updated(result.data.id, result.data.status))
        }
        return updateArtifactResult
    }
    override suspend fun addImages(artifactId: String, images: List<Uri>): RepositoryResult<ArtifactDto> {
        addedImages = images
        val result = addImagesResult
        if (result is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.Updated(result.data.id, result.data.status))
        }
        return addImagesResult
    }
    override suspend fun removeImage(artifactId: String, imageName: String): RepositoryResult<ArtifactDto> = RepositoryResult.Error("unused")
    override suspend fun setPrimaryImage(artifactId: String, imagePath: String): RepositoryResult<ArtifactDto> {
        primaryImagePathSet = imagePath
        val result = setPrimaryImageResult
        if (result is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.Updated(result.data.id, result.data.status))
        }
        return setPrimaryImageResult
    }
    override suspend fun deleteArtifact(artifactId: String): RepositoryResult<String> {
        if (deleteArtifactResult is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.Deleted(artifactId))
        }
        return deleteArtifactResult
    }
    override suspend fun recognizeArtifact(image: Uri, limit: Int?): RepositoryResult<RecognitionResponseDto> {
        recognizedUri = image
        return recognitionResult
    }
    override suspend fun recognizeArtifactFile(image: File, limit: Int?): RepositoryResult<RecognitionResponseDto> {
        recognizedFile = image
        return recognitionResult
    }
    override suspend fun indexArtifact(artifactId: String): RepositoryResult<AiIndexResultResponse> {
        indexArtifactCalls += 1
        if (indexArtifactResult is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return indexArtifactResult
    }
    override suspend fun feedPendingAiLibrary(): RepositoryResult<AiLibraryFeedResponse> {
        feedAiLibraryCalls += 1
        if (feedAiLibraryResult is RepositoryResult.Success) {
            artifactMutationEvents.tryEmit(ArtifactMutationEvent.AiLibraryUpdated)
        }
        return feedAiLibraryResult
    }
    override suspend fun indexAllArtifacts(): RepositoryResult<AiIndexAllResponse> = indexAllResult
    override suspend fun retryFailedIndexes(): RepositoryResult<AiIndexAllResponse> = retryFailedResult
    override suspend fun rebuildArtifactIndex(): RepositoryResult<AiIndexAllResponse> {
        rebuildCalls += 1
        return rebuildResult
    }
    override suspend fun indexStatus(): RepositoryResult<AiIndexStatusResponse> = indexStatusResult

    companion object {
        fun sampleCategory(
            id: String = "category-1",
            name: String = "Farm Tools",
            isActive: Boolean = true,
            artifactCount: Int = 0
        ): ArtifactCategoryDto = ArtifactCategoryDto(
            id = id,
            name = name,
            normalizedName = name.trim().lowercase(),
            isActive = isActive,
            artifactCount = artifactCount,
            createdAt = "2026-08-03T11:00:00",
            updatedAt = "2026-08-03T12:00:00"
        )

        fun sampleArtifact(
            status: String = "published",
            imageCount: Int = 1,
            primaryIndex: Int = 0,
            visitorGalleryImagePaths: List<String> = emptyList(),
            visitorGalleryConfigured: Boolean = visitorGalleryImagePaths.isNotEmpty(),
            metadataSections: List<ArtifactMetadataSectionDto> = emptyList()
        ): ArtifactDto {
            val paths = (1..imageCount).map { "uploads/images/image-$it.jpg" }
            val urls = paths.map { "http://testserver/$it" }
            val primaryPath = paths.getOrNull(primaryIndex)
            val primaryUrl = urls.getOrNull(primaryIndex)
            return ArtifactDto(
            id = "artifact-1",
            artifactCode = "ART-1",
            name = "Wooden Plow",
            description = "A traditional farming tool.",
            category = if (status == "published") "Farm Tools" else "Uncategorized",
            status = status,
            origin = null,
            historicalPeriod = null,
            material = null,
            dimensions = null,
            condition = null,
            customFields = listOf(ArtifactCustomFieldDto("local", "Local Name", "Araro", null, "text")),
            metadataSections = metadataSections,
            imagePaths = paths,
            imageUrls = urls,
            primaryImagePath = primaryPath,
            primaryImageUrl = primaryUrl,
            primaryImageNeedsReview = false,
            visitorGalleryImagePaths = visitorGalleryImagePaths,
            visitorGalleryImageUrls = visitorGalleryImagePaths.map { path -> "http://testserver/$path" },
            visitorGalleryConfigured = visitorGalleryConfigured,
            createdBy = "admin",
            createdAt = "2026-08-03T11:00:00",
            updatedAt = "2026-08-03T12:00:00"
            )
        }

        fun sampleMetadataSection(
            id: String = "section-acquisition",
            title: String = "Acquisition",
            fields: List<ArtifactMetadataFieldDto> = listOf(
                ArtifactMetadataFieldDto("field-acquired-from", "Acquired From", "Donated by alumni", "text", null, 0)
            )
        ): ArtifactMetadataSectionDto = ArtifactMetadataSectionDto(
            id = id,
            title = title,
            order = 2,
            fields = fields
        )
    }
}
