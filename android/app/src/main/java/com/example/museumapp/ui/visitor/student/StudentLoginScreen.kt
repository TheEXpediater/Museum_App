package com.example.museumapp.ui.visitor.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLoginScreen(
    repository: VisitorRepositoryContract,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: StudentLoginViewModel = viewModel(factory = StudentLoginViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Login") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(VisitorSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
        ) {
            Text("Welcome back.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = uiState.identifier,
                onValueChange = viewModel::updateIdentifier,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Student ID or Email") },
                singleLine = true,
                isError = uiState.errors["identifier"] != null,
                supportingText = { uiState.errors["identifier"]?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                isError = uiState.errors["password"] != null,
                supportingText = { uiState.errors["password"]?.let { Text(it) } },
                visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            if (uiState.passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (uiState.passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() })
            )
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::login, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                if (uiState.isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                Text("Log In")
            }
        }
    }
}
