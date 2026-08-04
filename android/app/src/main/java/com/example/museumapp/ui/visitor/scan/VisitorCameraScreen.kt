package com.example.museumapp.ui.visitor.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.admin.recognition.RecognitionImagePreparer
import com.example.museumapp.ui.admin.recognition.RecognitionUiMode
import com.example.museumapp.ui.admin.recognition.RecognitionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorCameraScreen(
    repository: VisitorRepositoryContract,
    onBack: () -> Unit,
    onViewArtifact: (String) -> Unit
) {
    val viewModel: RecognitionViewModel = viewModel(factory = RecognitionViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState by rememberSaveable {
        mutableStateOf(if (context.hasCameraPermission()) CameraPermissionUiState.Granted else CameraPermissionUiState.NotRequested)
    }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasRequestedPermission = true
        permissionState = when {
            granted -> CameraPermissionUiState.Granted
            context.isCameraPermanentlyDenied() -> CameraPermissionUiState.PermanentlyDenied
            else -> CameraPermissionUiState.Denied
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Artifact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (permissionState) {
                CameraPermissionUiState.NotRequested -> PermissionCard(
                    message = "Camera access is needed to scan artifacts.",
                    primary = "Allow Camera",
                    onPrimary = {
                        hasRequestedPermission = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onSettings = null
                )
                CameraPermissionUiState.Denied -> PermissionCard(
                    message = "Camera permission was denied.",
                    primary = "Try Again",
                    onPrimary = {
                        hasRequestedPermission = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onSettings = { context.openApplicationSettings() }
                )
                CameraPermissionUiState.PermanentlyDenied -> PermissionCard(
                    message = "Camera permission was denied.",
                    primary = "Open Settings",
                    onPrimary = { context.openApplicationSettings() },
                    onSettings = null
                )
                CameraPermissionUiState.Granted -> VisitorCameraGrantedContent(uiState, viewModel, onViewArtifact)
            }
        }
    }
}

@Composable
private fun VisitorCameraGrantedContent(
    uiState: com.example.museumapp.ui.admin.recognition.RecognitionUiState,
    viewModel: RecognitionViewModel,
    onViewArtifact: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller, lifecycleOwner) {
        var disposed = false
        viewModel.onCameraInitializing()
        try {
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            controller.bindToLifecycle(lifecycleOwner)
            controller.initializationFuture.addListener(
                {
                    if (!disposed) {
                        runCatching { controller.initializationFuture.get() }
                        viewModel.onCameraReady(controller.cameraInfo?.hasFlashUnit() == true)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (exception: SecurityException) {
            viewModel.onCameraError("Camera permission was denied.")
        } catch (exception: RuntimeException) {
            viewModel.onCameraError("The camera is not ready. Please scan again.")
        }
        onDispose {
            disposed = true
            runCatching { controller.cameraControl?.enableTorch(false) }
            runCatching { controller.unbind() }
        }
    }
    DisposableEffect(controller, cameraExecutor) {
        onDispose {
            cameraExecutor.shutdown()
            viewModel.setTorchEnabled(false)
        }
    }

    LaunchedEffect(uiState.torchEnabled, uiState.hasFlashUnit) {
        if (uiState.hasFlashUnit) runCatching { controller.cameraControl?.enableTorch(uiState.torchEnabled) }
    }

    when (uiState.mode) {
        RecognitionUiMode.Success -> RecognitionResultCard(uiState.response, onViewArtifact, viewModel::scanAgain)
        RecognitionUiMode.NoMatch -> NoMatchCard(viewModel::scanAgain)
        RecognitionUiMode.Failure -> FailureCard(uiState.errorMessage ?: "Artifact scanning is temporarily unavailable.", viewModel::scanAgain)
        else -> ScannerContent(
            uiState = uiState,
            controller = controller,
            cameraExecutor = cameraExecutor,
            scope = scope,
            viewModel = viewModel
        )
    }
}

@Composable
private fun ScannerContent(
    uiState: com.example.museumapp.ui.admin.recognition.RecognitionUiState,
    controller: LifecycleCameraController,
    cameraExecutor: ExecutorService,
    scope: CoroutineScope,
    viewModel: RecognitionViewModel
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            )
            if (uiState.mode == RecognitionUiMode.Capturing || uiState.mode == RecognitionUiMode.Processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.46f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Color.White)
                        Text(if (uiState.mode == RecognitionUiMode.Capturing) "Capturing" else "Analyzing", color = Color.White)
                    }
                }
            }
        }
        Text("Place the artifact inside the frame.", style = MaterialTheme.typography.titleMedium)
        Text("Hold the camera steady, then press Scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { viewModel.setTorchEnabled(!uiState.torchEnabled) },
                modifier = Modifier.weight(1f),
                enabled = uiState.hasFlashUnit
            ) {
                Icon(if (uiState.torchEnabled) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn, contentDescription = null)
                Text(if (uiState.torchEnabled) "Torch Off" else "Torch")
            }
            Button(
                onClick = { captureVisitorImage(context, controller, cameraExecutor, scope, viewModel) },
                modifier = Modifier.weight(1f),
                enabled = uiState.canRecognize
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Text("Scan")
            }
        }
        uiState.recognitionBlockedMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun captureVisitorImage(
    context: Context,
    controller: LifecycleCameraController,
    cameraExecutor: ExecutorService,
    scope: CoroutineScope,
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
    try {
        controller.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch(Dispatchers.IO) {
                        val prepared: File = try {
                            RecognitionImagePreparer.prepareCapturedImage(context, rawFile)
                        } catch (exception: RuntimeException) {
                            withContext(Dispatchers.Main.immediate) {
                                viewModel.onCaptureFailed(exception.message ?: "The captured image could not be processed.", rawFile)
                            }
                            return@launch
                        }
                        withContext(Dispatchers.Main.immediate) {
                            viewModel.processCapturedFile(prepared)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainExecutor.execute {
                        viewModel.onCaptureFailed("Camera capture failed. Please try again.", rawFile)
                    }
                }
            }
        )
    } catch (exception: SecurityException) {
        viewModel.onCaptureFailed("Camera permission was denied.", rawFile)
    } catch (exception: RuntimeException) {
        viewModel.onCaptureFailed("Camera capture failed. Please try again.", rawFile)
    }
}

@Composable
private fun RecognitionResultCard(response: RecognitionResponseDto?, onViewArtifact: (String) -> Unit, onScanAgain: () -> Unit) {
    val best = response?.bestMatch
    if (best == null) {
        FailureCard("Artifact scanning is temporarily unavailable.", onScanAgain)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Best Match", style = MaterialTheme.typography.headlineSmall)
        MatchCard(best, response.matchLevel, onViewArtifact)
        if (response.otherMatches.isNotEmpty()) {
            Text("Alternative matches", style = MaterialTheme.typography.titleMedium)
            response.otherMatches.forEach { MatchCard(it, "possible", onViewArtifact) }
        }
        OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Scan Again")
        }
    }
}

@Composable
private fun MatchCard(match: ArtifactMatchDto, level: String, onViewArtifact: (String) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (match.artifact.primaryImageUrl.isNullOrBlank()) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(if (level == "strong") "Strong Match" else "Possible Match", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text(match.artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(match.artifact.artifactCode, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(match.artifact.category, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onViewArtifact(match.artifact.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Text("View Artifact")
                }
            }
        }
    }
}

@Composable
private fun NoMatchCard(onScanAgain: () -> Unit) {
    FailureCard(
        message = "No reliable artifact match was found.\n\nMove closer and keep the artifact inside the frame.",
        onScanAgain = onScanAgain
    )
}

@Composable
private fun FailureCard(message: String, onScanAgain: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Scan Again")
            }
        }
    }
}

@Composable
private fun PermissionCard(message: String, primary: String, onPrimary: () -> Unit, onSettings: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(message)
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Icon(if (primary == "Open Settings") Icons.Outlined.Settings else Icons.Outlined.CameraAlt, contentDescription = null)
                Text(primary)
            }
            if (onSettings != null) {
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text("Open Settings")
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
