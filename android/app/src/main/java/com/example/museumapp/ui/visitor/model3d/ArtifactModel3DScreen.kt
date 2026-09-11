package com.example.museumapp.ui.visitor.model3d

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.model3d.Model3DCacheRepository
import com.google.android.filament.Skybox
import com.google.android.filament.utils.KTX1Loader
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.colorOf
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.utils.readBuffer
import java.io.File

/**
 * Neutral, museum-appropriate viewer background (linear color space, before Filament's
 * tone-mapping) - a soft light gray rather than pure black or pure white, so dark/brown
 * artifacts (woven baskets, dark wood, etc.) stay clearly visible without the scene looking
 * blown out. Shared by both [Model3DViewerContent] call sites (Admin preview and Visitor view).
 */
private const val VIEWER_BACKGROUND_LINEAR_GRAY = 0.85f

/**
 * Renders the visitor-facing 3D object viewer for one artifact's published GLB model, using
 * SceneView's Compose [Scene] with its built-in orbit/pan/zoom camera manipulator -- no AR or
 * camera-passthrough functionality is involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactModel3DScreen(
    repository: VisitorRepositoryContract,
    artifactId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArtifactModel3DViewModel = viewModel(
        key = "visitor_artifact_model3d_$artifactId",
        factory = ArtifactModel3DViewModel.factory(
            repository = repository,
            cacheRepository = remember { Model3DCacheRepository(context.applicationContext) },
            artifactId = artifactId
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Model", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                is Model3DViewerState.Loading -> CircularProgressIndicator()
                is Model3DViewerState.Downloading -> DownloadingContent(current.progressFraction)
                is Model3DViewerState.Ready -> Model3DViewerContent(current.localFilePath)
                is Model3DViewerState.Error -> ErrorContent(current.message, onRetry = viewModel::retry)
            }
        }
    }
}

/**
 * Admin counterpart of [ArtifactModel3DScreen] for reviewing a PENDING_REVIEW draft model before
 * Accept/Reject. Renders with the exact same SceneView pipeline (one engine/model loader per
 * screen instance, via [ArtifactModel3DViewModel.factoryForDraft]) - the only difference from the
 * visitor screen is where the url/version/sha256 come from, since a draft is never visible
 * through the visitor endpoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModel3DPreviewScreen(
    artifactId: String,
    version: Int,
    sha256: String,
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ArtifactModel3DViewModel = viewModel(
        key = "admin_draft_model3d_${artifactId}_$version",
        factory = ArtifactModel3DViewModel.factoryForDraft(
            cacheRepository = remember { Model3DCacheRepository(context.applicationContext) },
            artifactId = artifactId,
            version = version,
            sha256 = sha256,
            url = url
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Preview (Draft)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                is Model3DViewerState.Loading -> CircularProgressIndicator()
                is Model3DViewerState.Downloading -> DownloadingContent(current.progressFraction)
                is Model3DViewerState.Ready -> Model3DViewerContent(current.localFilePath)
                is Model3DViewerState.Error -> ErrorContent(current.message, onRetry = viewModel::retry)
            }
        }
    }
}

@Composable
private fun DownloadingContent(progressFraction: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val fraction = progressFraction
        if (fraction != null) {
            CircularProgressIndicator(progress = { fraction })
            Text("${(fraction * 100).toInt()}%")
        } else {
            CircularProgressIndicator()
        }
        Text("Downloading 3D model…")
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Cheap filesystem checks that must pass before a cached model file is handed to Filament.
 * A missing/empty file fed into the engine is not a recoverable Kotlin exception - it is a
 * native abort - so this is deliberately a plain, unit-testable function checked up front
 * rather than left for [io.github.sceneview.loaders.ModelLoader] to discover.
 */
internal fun validateCachedModelFile(file: File): String? {
    if (!file.isFile || file.length() <= 0L) {
        return "Downloaded 3D model is missing or empty."
    }
    return null
}

@Composable
private fun Model3DViewerContent(localFilePath: String) {
    var attempt by remember(localFilePath) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Reuses the exact same "neutral" studio indirect-light SceneView's own default environment
    // loads (environments/neutral/neutral_ibl.ktx, bundled in the SceneView AAR's assets) - only
    // the skybox fill color is overridden, from SceneView's default pure black to a light,
    // neutral gray. See SceneView.createEnvironment()'s default skybox (colorOf(rgb = 0f)) in the
    // pinned 2.3.0 sources for the code this mirrors.
    val environment = rememberEnvironment(environmentLoader) {
        SceneView.createEnvironment(
            engine = engine,
            isOpaque = true,
            indirectLight = KTX1Loader.createIndirectLight(
                engine,
                context.assets.readBuffer(fileLocation = "environments/neutral/neutral_ibl.ktx")
            ),
            skybox = Skybox.Builder()
                .color(colorOf(rgb = VIEWER_BACKGROUND_LINEAR_GRAY, a = 1f).toFloatArray())
                .build(engine)
        )
    }
    val cameraNode = rememberCameraNode(engine)
    var modelInstance by remember(localFilePath, attempt) { mutableStateOf<ModelInstance?>(null) }
    var loadError by remember(localFilePath, attempt) { mutableStateOf<String?>(null) }

    LaunchedEffect(localFilePath, attempt) {
        modelInstance = null
        loadError = null
        try {
            val file = File(localFilePath)
            validateCachedModelFile(file)?.let { error(it) }

            // Must run on the same thread that owns the Filament engine (rememberEngine() creates
            // it on this composition's thread, i.e. Main) -- Filament's asset/resource loading
            // panics with a native SIGABRT ("This thread has not been adopted") when called from
            // another thread such as Dispatchers.IO, since only the owning thread is "adopted"
            // into the engine's rendering context. LaunchedEffect already runs on Main by default,
            // so no dispatcher switch is needed or safe here; this previously reproduced as a real
            // crash on a physical device.
            modelInstance = modelLoader.createModelInstance(file)
        } catch (throwable: Throwable) {
            loadError = throwable.message?.takeIf { it.isNotBlank() } ?: "Could not display this 3D preview."
        }
    }

    when {
        loadError != null -> ErrorContent(loadError.orEmpty(), onRetry = { attempt += 1 })
        modelInstance == null -> CircularProgressIndicator()
        else -> {
            val instance = modelInstance!!
            val modelNode = remember(instance) {
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = true,
                    scaleToUnits = 1f
                ).also { node ->
                    // COLMAP's reconstructed geometry is not centered at its own local origin -
                    // the local (0,0,0) pivot can sit far outside the actual point cloud entirely.
                    // ModelNode.centerOrigin(Position(0,0,0)) (used previously here) computes
                    // `position += origin * size`, which is a no-op whenever origin is zero, so it
                    // never actually recentered a mesh like this one. Compute the model's real
                    // world-space bounds (local bounding-box center/extent scaled by the uniform
                    // scaleToUnits factor applied above, since position/rotation are left at
                    // identity) and frame the CAMERA at that point instead of fighting the node's
                    // transform - this is the "normalize only the view transform" approach for
                    // arbitrary reconstructed meshes that aren't authored with a conventional pivot.
                    val worldCenter = node.center * node.scale
                    Log.d(
                        "Model3DViewer",
                        "model bounds: localCenter=${node.center} localExtents=${node.extents} " +
                            "scale=${node.scale} worldCenter=$worldCenter vertexCount=" +
                            "${node.renderableNodes.size} nodes"
                    )
                    cameraNode.position = worldCenter + Position(x = 0.9f, y = 0.6f, z = 1.6f)
                    cameraNode.lookAt(worldCenter)
                }
            }
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                environment = environment,
                cameraNode = cameraNode,
                childNodes = rememberNodes { add(modelNode) },
                cameraManipulator = rememberCameraManipulator(cameraNode.worldPosition)
            )
        }
    }
}
