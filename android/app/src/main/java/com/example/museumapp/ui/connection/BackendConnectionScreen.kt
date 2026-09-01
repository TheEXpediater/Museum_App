package com.example.museumapp.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.network.BackendConnectionState

/**
 * The gate shown before any Visitor/Admin content: checks the saved backend address, falls back
 * to scanning the current local network, and asks for a manual address only if both fail. Mirrors
 * the flow specified for the museum handoff (no mDNS/NSD/Bonjour).
 */
@Composable
fun BackendConnectionGate(state: BackendConnectionState, manager: BackendConnectionManager) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is BackendConnectionState.CheckingSavedBackend ->
                StatusMessage("Starting Museum System", "Checking the saved backend address...")

            is BackendConnectionState.SearchingLocalNetwork ->
                StatusMessage("Starting Museum System", "Looking for the museum backend on this network...")

            is BackendConnectionState.Connecting ->
                StatusMessage("Connecting", "Trying ${state.host}:${state.port}...")

            is BackendConnectionState.BackendNotFound ->
                ManualEntryContent(
                    title = "Backend Not Found",
                    explanation = "The museum backend could not be detected on this network. Make sure the phone and the laptop are connected to the same Wi-Fi network or mobile hotspot.",
                    errorMessage = null,
                    manager = manager
                )

            is BackendConnectionState.ConnectionFailed ->
                ManualEntryContent(
                    title = "Backend Not Found",
                    explanation = "Enter the laptop's backend address below.",
                    errorMessage = state.message,
                    manager = manager
                )

            is BackendConnectionState.Connected -> Unit
        }
    }
}

@Composable
private fun StatusMessage(title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ManualEntryContent(
    title: String,
    explanation: String,
    errorMessage: String?,
    manager: BackendConnectionManager
) {
    var addressInput by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Laptop backend address") },
                placeholder = { Text("192.168.x.x:8000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val (host, port) = parseAddress(addressInput)
                    if (host != null) manager.connectManually(host, port)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = addressInput.isNotBlank()
            ) {
                Text("Connect")
            }
        }
    }
}

private fun parseAddress(raw: String): Pair<String?, Int> {
    val trimmed = raw.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    if (trimmed.isEmpty()) return null to BackendConnectionManager.DEFAULT_PORT
    val lastColon = trimmed.lastIndexOf(':')
    if (lastColon <= 0) return trimmed to BackendConnectionManager.DEFAULT_PORT
    val host = trimmed.substring(0, lastColon)
    val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: BackendConnectionManager.DEFAULT_PORT
    return host to port
}
