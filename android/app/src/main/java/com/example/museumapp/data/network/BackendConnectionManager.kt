package com.example.museumapp.data.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.museumapp.data.model.HealthResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val Context.backendConnectionDataStore by preferencesDataStore(name = "backend_connection")

/**
 * States mirror the flow specified for the museum handoff: try the saved address first, fall back
 * to scanning the phone's current /24 subnet for the backend health endpoint, and only ask the
 * user for a manual address if neither works. No mDNS/NSD/Bonjour is used.
 */
sealed interface BackendConnectionState {
    data object CheckingSavedBackend : BackendConnectionState
    data object SearchingLocalNetwork : BackendConnectionState
    data class Connecting(val host: String, val port: Int) : BackendConnectionState
    data class Connected(val host: String, val port: Int) : BackendConnectionState
    data object BackendNotFound : BackendConnectionState
    data class ConnectionFailed(val message: String) : BackendConnectionState
}

/**
 * Single source of truth for the laptop backend address. The Android app never hardcodes a LAN
 * IP: it is discovered at runtime and persisted only after a real health check succeeds.
 */
class BackendConnectionManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BackendConnectionState>(BackendConnectionState.CheckingSavedBackend)
    val state: StateFlow<BackendConnectionState> = _state.asStateFlow()

    @Volatile var activeHost: String? = null
        private set

    @Volatile var activePort: Int = DEFAULT_PORT
        private set

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val healthAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(HealthResponse::class.java)

    /** Runs the saved-IP-then-local-search flow. Safe to call again (e.g. app resume, retry). */
    fun start() {
        scope.launch { runDiscovery() }
    }

    /** Re-runs discovery from scratch, e.g. after the user asks to change the backend. */
    fun retry() {
        start()
    }

    /** Lets any screen (Settings, etc.) send the user back to the connection gate. */
    fun requestManualEntry() {
        _state.value = BackendConnectionState.BackendNotFound
    }

    fun connectManually(host: String, port: Int) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) {
            _state.value = BackendConnectionState.ConnectionFailed("Enter the laptop's backend address.")
            return
        }
        scope.launch {
            _state.value = BackendConnectionState.Connecting(trimmedHost, port)
            if (probe(trimmedHost, port)) {
                applyConnected(trimmedHost, port, persist = true)
            } else {
                _state.value = BackendConnectionState.ConnectionFailed(
                    "Could not reach a museum backend at $trimmedHost:$port. Check the address and that both devices share the same network."
                )
            }
        }
    }

    private suspend fun runDiscovery() {
        _state.value = BackendConnectionState.CheckingSavedBackend
        val saved = readSaved()
        if (saved != null && probe(saved.first, saved.second)) {
            applyConnected(saved.first, saved.second, persist = false)
            return
        }

        _state.value = BackendConnectionState.SearchingLocalNetwork
        val hosts = candidateHostsOnCurrentNetwork()
        val found = if (hosts.isEmpty()) null else probeConcurrently(hosts, DEFAULT_PORT)
        if (found != null) {
            applyConnected(found, DEFAULT_PORT, persist = true)
        } else {
            _state.value = BackendConnectionState.BackendNotFound
        }
    }

    private suspend fun applyConnected(host: String, port: Int, persist: Boolean) {
        activeHost = host
        activePort = port
        if (persist) persistSaved(host, port)
        _state.value = BackendConnectionState.Connected(host, port)
    }

    private suspend fun probe(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("http://$host:$port/api/v1/health").get().build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string().orEmpty()
                val health = healthAdapter.fromJson(body)
                !health?.status.isNullOrBlank()
            }
        }.getOrDefault(false)
    }

    private suspend fun probeConcurrently(hosts: List<String>, port: Int): String? = coroutineScope {
        val found = AtomicReference<String?>(null)
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        val jobs = hosts.map { host ->
            launch {
                if (found.get() != null) return@launch
                semaphore.withPermit {
                    if (found.get() != null) return@withPermit
                    if (probe(host, port)) {
                        found.compareAndSet(null, host)
                    }
                }
            }
        }
        jobs.joinAll()
        found.get()
    }

    private fun candidateHostsOnCurrentNetwork(): List<String> {
        val ip = deviceIpv4() ?: return emptyList()
        val lastDot = ip.lastIndexOf('.')
        if (lastDot <= 0) return emptyList()
        val subnetPrefix = ip.substring(0, lastDot)
        val selfLastOctet = ip.substring(lastDot + 1).toIntOrNull() ?: return emptyList()
        return (1..254).filter { it != selfLastOctet }.map { "$subnetPrefix.$it" }
    }

    private fun deviceIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (exception: SocketException) {
        null
    }

    private suspend fun readSaved(): Pair<String, Int>? {
        val preferences = appContext.backendConnectionDataStore.data.first()
        val host = preferences[Keys.Host] ?: return null
        val port = preferences[Keys.Port] ?: DEFAULT_PORT
        return host to port
    }

    private suspend fun persistSaved(host: String, port: Int) {
        appContext.backendConnectionDataStore.edit { preferences ->
            preferences[Keys.Host] = host
            preferences[Keys.Port] = port
        }
    }

    private object Keys {
        val Host = stringPreferencesKey("backend_host")
        val Port = intPreferencesKey("backend_port")
    }

    companion object {
        const val DEFAULT_PORT = 8000
        private const val PROBE_TIMEOUT_MS = 1200L
        private const val MAX_CONCURRENT_PROBES = 24
    }
}
