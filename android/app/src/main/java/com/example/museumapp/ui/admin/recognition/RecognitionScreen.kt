package com.example.museumapp.ui.admin.recognition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.MatchLevelChip
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun RecognitionScreen(
    repository: AdminRepositoryContract,
    padding: PaddingValues,
    onOpenSystemStatus: () -> Unit,
    onViewIndexedArtifacts: () -> Unit,
    onViewArtifact: (String) -> Unit
) {
    val viewModel: RecognitionViewModel = viewModel(factory = RecognitionViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState by rememberSaveable {
        mutableStateOf(
            if (context.hasCameraPermission()) CameraPermissionUiState.Granted else CameraPermissionUiState.NotRequested
        )
    }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermission = true
        permissionState = when {
            granted -> CameraPermissionUiState.Granted
            context.isCameraPermanentlyDenied() -> CameraPermissionUiState.PermanentlyDenied
            else -> CameraPermissionUiState.Denied
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            viewModel.selectImage(uri)
            viewModel.recognize()
        }
    )

    DisposableEffect(lifecycleOwner, hasRequestedPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = when {
                    context.hasCameraPermission() -> CameraPermissionUiState.Granted
                    hasRequestedPermission && context.isCameraPermanentlyDenied() -> CameraPermissionUiState.PermanentlyDenied
                    hasRequestedPermission -> CameraPermissionUiState.Denied
                    else -> CameraPermissionUiState.NotRequested
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI Recognition", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Admin camera test",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            ReadinessCard(
                uiState = uiState,
                onOpenSystemStatus = onOpenSystemStatus
            )
        }
        item {
            when (permissionState) {
                CameraPermissionUiState.NotRequested -> PermissionPromptCard(
                    title = "Camera access is required to capture an artifact for recognition.",
                    primaryLabel = "Allow Camera",
                    onPrimary = {
                        hasRequestedPermission = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = null
                )
                CameraPermissionUiState.Denied -> PermissionPromptCard(
                    title = "Camera permission was denied.",
                    primaryLabel = "Try Again",
                    onPrimary = {
                        hasRequestedPermission = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = { context.openApplicationSettings() }
                )
                CameraPermissionUiState.PermanentlyDenied -> PermissionPromptCard(
                    title = "Camera permission was denied.",
                    primaryLabel = "Open Settings",
                    onPrimary = { context.openApplicationSettings() },
                    onOpenSettings = null
                )
                CameraPermissionUiState.Granted -> RecognitionGrantedContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPickGallery = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onOpenSystemStatus = onOpenSystemStatus,
                    onViewIndexedArtifacts = onViewIndexedArtifacts,
                    onViewArtifact = onViewArtifact
                )
            }
        }
    }
}

@Composable
private fun RecognitionGrantedContent(
    uiState: RecognitionUiState,
    viewModel: RecognitionViewModel,
    onPickGallery: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onViewIndexedArtifacts: () -> Unit,
    onViewArtifact: (String) -> Unit
) {
    Crossfade(targetState = uiState.mode, label = "recognition-state") { mode ->
        when (mode) {
            RecognitionUiMode.Success -> RecognitionResult(
                response = uiState.response,
                onScanAgain = viewModel::scanAgain,
                onViewArtifact = onViewArtifact,
                onViewIndexedArtifacts = onViewIndexedArtifacts
            )
            RecognitionUiMode.NoMatch -> NoMatchCard(
                message = uiState.response?.message,
                onScanAgain = viewModel::scanAgain,
                onViewIndexedArtifacts = onViewIndexedArtifacts
            )
            RecognitionUiMode.Failure -> FailureCard(
                message = uiState.errorMessage ?: "Recognition failed. Please try again.",
                onScanAgain = viewModel::scanAgain,
                onOpenSystemStatus = onOpenSystemStatus
            )
            RecognitionUiMode.CameraInitializing,
            RecognitionUiMode.CameraReady,
            RecognitionUiMode.Capturing,
            RecognitionUiMode.Processing -> CameraScannerCard(
                uiState = uiState,
                viewModel = viewModel,
                onPickGallery = onPickGallery,
                onOpenSystemStatus = onOpenSystemStatus
            )
        }
    }
}

@Composable
private fun ReadinessCard(
    uiState: RecognitionUiState,
    onOpenSystemStatus: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recognition readiness", style = MaterialTheme.typography.titleMedium)
                if (uiState.recognitionBlockedMessage == null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            "Ready",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text(
                if (uiState.indexedVectors > 0) {
                    "${uiState.indexedVectors} indexed vector point(s) ready"
                } else {
                    "No indexed artifact images are available."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "OpenCLIP ${uiState.aiStatus.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.recognitionBlockedMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onOpenSystemStatus, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text("Open System Status")
                }
            }
        }
    }
}

@Composable
internal fun PermissionPromptCard(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Text(primaryLabel)
            }
            if (onOpenSettings != null) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun CameraScannerCard(
    uiState: RecognitionUiState,
    viewModel: RecognitionViewModel,
    onPickGallery: () -> Unit,
    onOpenSystemStatus: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        viewModel.onCameraInitializing()
        runCatching {
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            controller.bindToLifecycle(lifecycleOwner)
            viewModel.onCameraReady(controller.cameraInfo?.hasFlashUnit() == true)
        }.onFailure {
            viewModel.onCameraError("Camera capture failed. Please try again.")
        }
        onDispose {
            runCatching { controller.cameraControl?.enableTorch(false) }
            runCatching { controller.unbind() }
        }
    }

    LaunchedEffect(uiState.torchEnabled, uiState.hasFlashUnit) {
        if (uiState.hasFlashUnit) {
            runCatching { controller.cameraControl?.enableTorch(uiState.torchEnabled) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { controller.cameraControl?.enableTorch(false) }
            cameraExecutor.shutdown()
            viewModel.setTorchEnabled(false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("camera_scanner")
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            this.controller = controller
                        }
                    },
                    update = { previewView -> previewView.controller = controller },
                    modifier = Modifier.fillMaxSize()
                )
                ScannerOverlay(
                    uiState = uiState,
                    controller = controller,
                    cameraExecutor = cameraExecutor,
                    viewModel = viewModel
                )
            }
        }
        uiState.recognitionBlockedMessage?.let { message ->
            RecognitionBlockedCard(message = message, onOpenSystemStatus = onOpenSystemStatus)
        }
        OutlinedButton(
            onClick = onPickGallery,
            enabled = !uiState.isRecognizing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Text("Choose From Gallery")
        }
    }
}

@Composable
private fun ScannerOverlay(
    uiState: RecognitionUiState,
    controller: LifecycleCameraController,
    cameraExecutor: ExecutorService,
    viewModel: RecognitionViewModel
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        ScannerFrame(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.76f)
                .aspectRatio(0.78f)
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Place the artifact inside the frame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "Hold the camera steady, then press Recognize.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )
        }
        if (uiState.hasFlashUnit) {
            IconButton(
                onClick = {
                    val next = !uiState.torchEnabled
                    runCatching { controller.cameraControl?.enableTorch(next) }
                    viewModel.setTorchEnabled(next)
                },
                enabled = uiState.mode == RecognitionUiMode.CameraReady,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = if (uiState.torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = if (uiState.torchEnabled) "Turn torch off" else "Turn torch on",
                    tint = Color.White
                )
            }
        }
        RecognitionCaptureButton(
            uiState = uiState,
            onClick = {
                captureRecognitionImage(
                    context = context,
                    controller = controller,
                    cameraExecutor = cameraExecutor,
                    viewModel = viewModel
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(16.dp)
        )
        if (uiState.mode == RecognitionUiMode.CameraInitializing) {
            BlockingOverlay(message = "Opening rear camera...")
        }
        if (uiState.mode == RecognitionUiMode.Processing) {
            BlockingOverlay(message = "Analyzing artifact...")
        }
    }
}

@Composable
private fun ScannerFrame(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .border(2.dp, color, RoundedCornerShape(20.dp))
            .padding(1.dp)
    ) {
        CornerMarker(Alignment.TopStart, horizontal = true, color = color)
        CornerMarker(Alignment.TopStart, horizontal = false, color = color)
        CornerMarker(Alignment.TopEnd, horizontal = true, color = color)
        CornerMarker(Alignment.TopEnd, horizontal = false, color = color)
        CornerMarker(Alignment.BottomStart, horizontal = true, color = color)
        CornerMarker(Alignment.BottomStart, horizontal = false, color = color)
        CornerMarker(Alignment.BottomEnd, horizontal = true, color = color)
        CornerMarker(Alignment.BottomEnd, horizontal = false, color = color)
    }
}

@Composable
private fun BoxScope.CornerMarker(alignment: Alignment, horizontal: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(
                width = if (horizontal) 46.dp else 5.dp,
                height = if (horizontal) 5.dp else 46.dp
            )
            .background(color, RoundedCornerShape(4.dp))
    )
}

@Composable
internal fun RecognitionCaptureButton(
    uiState: RecognitionUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (uiState.mode) {
        RecognitionUiMode.Capturing -> "Capturing..."
        RecognitionUiMode.Processing -> "Analyzing artifact..."
        else -> "Recognize"
    }
    Button(
        onClick = onClick,
        enabled = uiState.canRecognize && uiState.mode == RecognitionUiMode.CameraReady,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .heightIn(min = 60.dp)
            .testTag("recognize_button")
    ) {
        if (uiState.mode == RecognitionUiMode.Capturing || uiState.mode == RecognitionUiMode.Processing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun BlockingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = Color.White)
            Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun captureRecognitionImage(
    context: Context,
    controller: LifecycleCameraController,
    cameraExecutor: ExecutorService,
    viewModel: RecognitionViewModel
) {
    if (!viewModel.beginCameraCapture()) return
    val rawFile = runCatching { RecognitionImagePreparer.createRawCaptureFile(context) }
        .getOrElse {
            viewModel.onCaptureFailed("The captured image could not be processed.")
            return
        }
    viewModel.trackTemporaryCapture(rawFile)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()
    val mainExecutor = ContextCompat.getMainExecutor(context)

    controller.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val preparedFile: File = try {
                    RecognitionImagePreparer.prepareCapturedImage(context, rawFile)
                } catch (exception: IllegalArgumentException) {
                    mainExecutor.execute { viewModel.onCaptureFailed(exception.message ?: "The captured image could not be processed.", rawFile) }
                    return
                } catch (exception: RuntimeException) {
                    RecognitionImagePreparer.deleteQuietly(rawFile)
                    mainExecutor.execute { viewModel.onCaptureFailed("The captured image could not be processed.") }
                    return
                }
                mainExecutor.execute { viewModel.processCapturedFile(preparedFile) }
            }

            override fun onError(exception: ImageCaptureException) {
                RecognitionImagePreparer.deleteQuietly(rawFile)
                mainExecutor.execute { viewModel.onCaptureFailed("Camera capture failed. Please try again.") }
            }
        }
    )
}

@Composable
private fun RecognitionBlockedCard(message: String, onOpenSystemStatus: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSystemStatus, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Text("Open System Status")
            }
        }
    }
}

@Composable
private fun RecognitionResult(
    response: RecognitionResponseDto?,
    onScanAgain: () -> Unit,
    onViewArtifact: (String) -> Unit,
    onViewIndexedArtifacts: () -> Unit
) {
    if (response == null) {
        FailureCard(
            message = "Recognition failed. Please try again.",
            onScanAgain = onScanAgain,
            onOpenSystemStatus = onViewIndexedArtifacts
        )
        return
    }
    if (!response.matched || response.bestMatch == null) {
        NoMatchCard(response.message, onScanAgain, onViewIndexedArtifacts)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("recognition_result")) {
        BestMatchCard(
            match = response.bestMatch,
            level = response.matchLevel,
            onViewArtifact = { onViewArtifact(response.bestMatch.artifact.id) }
        )
        if (response.otherMatches.isNotEmpty()) {
            Text("Alternative Matches", style = MaterialTheme.typography.titleLarge)
            response.otherMatches.forEach { match ->
                AlternativeMatchCard(
                    match = match,
                    onViewArtifact = { onViewArtifact(match.artifact.id) }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onViewArtifact(response.bestMatch.artifact.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("View Artifact Details")
            }
            OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Scan Again")
            }
        }
    }
}

@Composable
internal fun BestMatchCard(match: ArtifactMatchDto, level: String, onViewArtifact: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View Artifact Details", onClick = onViewArtifact)
            .testTag("best_match_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Best Match", style = MaterialTheme.typography.titleLarge)
                MatchLevelChip(level)
            }
            MatchArtifactContent(match, large = true)
            OutlinedButton(onClick = onViewArtifact, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("View Artifact Details")
            }
        }
    }
}

@Composable
internal fun AlternativeMatchCard(match: ArtifactMatchDto, onViewArtifact: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View Artifact Details", onClick = onViewArtifact)
            .testTag("alternative_match_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        MatchArtifactContent(match, large = false, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun MatchArtifactContent(match: ArtifactMatchDto, large: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 92.dp else 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (match.artifact.primaryImageUrl.isNullOrBlank()) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AsyncImage(
                    model = match.artifact.primaryImageUrl,
                    contentDescription = match.artifact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(match.artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.artifact.artifactCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(match.artifact.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (large) {
                Text(match.artifact.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Similarity Score ${formatScore(match.similarityScore)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${match.supportingImageHits} Supporting Image Hits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun NoMatchCard(message: String?, onScanAgain: () -> Unit, onViewIndexedArtifacts: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("no_match_result")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MatchLevelChip("no_match")
            Text("No reliable artifact match was found.", style = MaterialTheme.typography.titleMedium)
            if (!message.isNullOrBlank() && message != "No reliable artifact match was found.") {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Scan Again")
                }
                OutlinedButton(onClick = onViewIndexedArtifacts, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Text("View Indexed Artifacts")
                }
            }
        }
    }
}

@Composable
private fun FailureCard(message: String, onScanAgain: () -> Unit, onOpenSystemStatus: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("Recognition failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Scan Again")
                }
                TextButton(onClick = onOpenSystemStatus, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text("Open System Status")
                }
            }
        }
    }
}

private enum class CameraPermissionUiState {
    NotRequested,
    Granted,
    Denied,
    PermanentlyDenied
}

private fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun Context.isCameraPermanentlyDenied(): Boolean {
    val activity = findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun Context.openApplicationSettings() {
    startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun formatScore(score: Double): String = String.format(Locale.US, "%.3f", score)
