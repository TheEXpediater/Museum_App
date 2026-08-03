package com.example.museumapp.data.api

import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexAllResponse
import com.example.museumapp.data.model.AiIndexResultResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactListResponse
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.model.DeleteResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.LoginRequest
import com.example.museumapp.data.model.LoginResponse
import com.example.museumapp.data.model.PrimaryImageRequest
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.model.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApiService {
    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @GET("api/v1/ai/health")
    suspend fun aiHealth(): AiHealthResponse

    @POST("api/v1/ai/warmup")
    suspend fun warmupAi(): AiWarmupResponse

    @GET("api/v1/ai/warmup/status")
    suspend fun warmupAiStatus(): AiWarmupResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/v1/auth/me")
    suspend fun currentAdmin(): UserDto

    @GET("api/v1/admin/dashboard")
    suspend fun dashboardSummary(): DashboardSummaryResponse

    @GET("api/v1/artifacts")
    suspend fun listArtifacts(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("search") search: String?,
        @Query("category") category: String?,
        @Query("sort") sort: String
    ): ArtifactListResponse

    @GET("api/v1/artifacts/{artifactId}")
    suspend fun getArtifact(@Path("artifactId") artifactId: String): ArtifactDto

    @Multipart
    @POST("api/v1/artifacts")
    suspend fun createArtifact(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part images: List<MultipartBody.Part>
    ): ArtifactDto

    @Multipart
    @PATCH("api/v1/artifacts/{artifactId}")
    suspend fun updateArtifact(
        @Path("artifactId") artifactId: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part images: List<MultipartBody.Part>
    ): ArtifactDto

    @Multipart
    @POST("api/v1/artifacts/{artifactId}/images")
    suspend fun addImages(
        @Path("artifactId") artifactId: String,
        @Part images: List<MultipartBody.Part>
    ): ArtifactDto

    @DELETE("api/v1/artifacts/{artifactId}/images/{imageName}")
    suspend fun removeImage(
        @Path("artifactId") artifactId: String,
        @Path("imageName") imageName: String
    ): ArtifactDto

    @PATCH("api/v1/artifacts/{artifactId}/primary-image")
    suspend fun setPrimaryImage(
        @Path("artifactId") artifactId: String,
        @Body request: PrimaryImageRequest
    ): ArtifactDto

    @DELETE("api/v1/artifacts/{artifactId}")
    suspend fun deleteArtifact(@Path("artifactId") artifactId: String): DeleteResponse

    @Multipart
    @POST("api/v1/ai/recognize")
    suspend fun recognizeArtifact(
        @Part image: MultipartBody.Part,
        @Query("limit") limit: Int? = null
    ): RecognitionResponseDto

    @POST("api/v1/ai/index/artifacts/{artifactId}")
    suspend fun indexArtifact(@Path("artifactId") artifactId: String): AiIndexResultResponse

    @POST("api/v1/ai/index/all")
    suspend fun indexAllArtifacts(): AiIndexAllResponse

    @POST("api/v1/ai/index/failed")
    suspend fun retryFailedIndexes(): AiIndexAllResponse

    @POST("api/v1/ai/index/rebuild")
    suspend fun rebuildArtifactIndex(): AiIndexAllResponse

    @GET("api/v1/ai/index/status")
    suspend fun indexStatus(): AiIndexStatusResponse
}
