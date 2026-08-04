package com.example.museumapp.ui.visitor.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorFormValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationScreen(
    repository: VisitorRepositoryContract,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: StudentRegistrationViewModel = viewModel(factory = StudentRegistrationViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var yearExpanded by remember { mutableStateOf(false) }
    var programExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Student Account") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VisitorTextField("Student ID", uiState.studentId, viewModel::updateStudentId, uiState.errors["studentId"])
            VisitorTextField("First Name", uiState.firstName, viewModel::updateFirstName, uiState.errors["firstName"])
            VisitorTextField("Middle Initial", uiState.middleInitial, viewModel::updateMiddleInitial, uiState.errors["middleInitial"])
            VisitorTextField("Last Name", uiState.lastName, viewModel::updateLastName, uiState.errors["lastName"])

            DropdownField(
                label = "Year Level",
                value = uiState.yearLevel,
                expanded = yearExpanded,
                error = uiState.errors["yearLevel"],
                onExpandChange = { yearExpanded = it },
                options = VisitorFormValidation.YearLevels,
                onSelected = viewModel::updateYearLevel
            )

            if (uiState.allowsFreeTextCourse) {
                VisitorTextField("Course or Program", uiState.course, viewModel::updateCourse, uiState.errors["course"])
            } else {
                DropdownField(
                    label = "Course or Program",
                    value = uiState.course,
                    expanded = programExpanded,
                    error = uiState.errors["course"],
                    onExpandChange = { programExpanded = it },
                    options = uiState.programs.map { it.name },
                    onSelected = viewModel::updateCourse
                )
            }

            VisitorTextField("Email", uiState.email, viewModel::updateEmail, uiState.errors["email"], keyboardType = KeyboardType.Email)
            PasswordField(
                label = "Password",
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                visible = uiState.passwordVisible,
                onToggle = viewModel::togglePasswordVisibility,
                error = uiState.errors["password"],
                imeAction = ImeAction.Next
            )
            PasswordField(
                label = "Confirm Password",
                value = uiState.confirmPassword,
                onValueChange = viewModel::updateConfirmPassword,
                visible = uiState.confirmPasswordVisible,
                onToggle = viewModel::toggleConfirmPasswordVisibility,
                error = uiState.errors["confirmPassword"],
                imeAction = ImeAction.Done,
                onDone = viewModel::register
            )
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::register, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                if (uiState.isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                Text("Create Account")
            }
        }
    }
}

@Composable
private fun VisitorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next)
    )
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    error: String?,
    onExpandChange: (Boolean) -> Unit,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            trailingIcon = {
                IconButton(onClick = { onExpandChange(true) }) {
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Select $label")
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        onExpandChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
    error: String?,
    imeAction: ImeAction,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}
