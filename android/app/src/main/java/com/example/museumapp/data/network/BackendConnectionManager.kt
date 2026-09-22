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
 * States mirror the client-testing connection flow: try a previously working address first (hosted
 * or LAN), otherwise attempt the hosted VPS API by default, then fall back to scanning the phone's
 * current /24 subnet for a LAN museum backend, and only ask the user for a manual address if none
 * of those work. No mDNS/NSD/Bonjour is used.
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

    /**
     * "http" for the LAN museum backend, "https" for [connectHosted]. [BackendConnectionInterceptor]
     * reads this on every request so the hosted HTTPS endpoint is never silently downgraded to
     * cleartext or forced onto the LAN default port.
     */
    @Volatile var activeScheme: String = "http"
        private set

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** The hosted probe crosses the public internet (TLS handshake included), so it gets a more
     * generous timeout than the LAN subnet scan, which must stay fast across ~250 candidate hosts. */
    private val hostedProbeClient = OkHttpClient.Builder()
        .connectTimeout(HOSTED_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(HOSTED_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(HOSTED_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
            if (probe(trimmedHost, port, "http")) {
                applyConnected(trimmedHost, port, "http", persist = true)
            } else {
                _state.value = BackendConnectionState.ConnectionFailed(
                    "Could not reach a museum backend at $trimmedHost:$port. Check the address and that both devices share the same network."
                )
            }
        }
    }

    /**
     * Switches to the hosted VPS API (https://$HOSTED_HOST/) instead of the LAN museum backend.
     * This is the single source of truth for the hosted base URL - no other class hardcodes it.
     */
    fun connectHosted() {
        scope.launch {
            _state.value = BackendConnectionState.Connecting(HOSTED_HOST, HOSTED_PORT)
            if (probe(HOSTED_HOST, HOSTED_PORT, "https")) {
                applyConnected(HOSTED_HOST, HOSTED_PORT, "https", persist = true)
            } else {
                _state.value = BackendConnectionState.ConnectionFailed(
                    "Could not reach the hosted museum server at $HOSTED_HOST. Check the internet connection and try again."
                )
            }
        }
    }

    private suspend fun runDiscovery() {
        _state.value = BackendConnectionState.CheckingSavedBackend
        val saved = readSaved()
        if (saved != null && probe(saved.host, saved.port, saved.scheme)) {
            // A previously working connection (hosted or LAN) is tried first and, if it still
            // answers, used as-is. This keeps repeat launches fast and lets an offline museum
            // kiosk that is already using its LAN backend skip the hosted attempt below.
            applyConnected(saved.host, saved.port, saved.scheme, persist = false)
            return
        }

        // No known-working saved connection: the hosted VPS API is now the default first attempt
        // so a client/student on a fresh install reaches the museum without pressing anything.
        // LAN discovery remains the fallback for the offline museum deployment.
        _state.value = BackendConnectionState.Connecting(HOSTED_HOST, HOSTED_PORT)
        if (probe(HOSTED_HOST, HOSTED_PORT, "https")) {
            applyConnected(HOSTED_HOST, HOSTED_PORT, "https", persist = true)
            return
        }

        _state.value = BackendConnectionState.SearchingLocalNetwork
        val hosts = candidateHostsOnCurrentNetwork()
        val found = if (hosts.isEmpty()) null else probeConcurrently(hosts, DEFAULT_PORT)
        if (found != null) {
            applyConnected(found, DEFAULT_PORT, "http", persist = true)
        } else {
            _state.value = BackendConnectionState.BackendNotFound
        }
    }

    private suspend fun applyConnected(host: String, port: Int, scheme: String, persist: Boolean) {
        activeHost = host
        activePort = port
        activeScheme = scheme
        if (persist) persistSaved(host, port, scheme)
        _state.value = BackendConnectionState.Connected(host, port)
    }

    private suspend fun probe(host: String, port: Int, scheme: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$scheme://$host:$port/api/v1/health").get().build()
            val client = if (scheme == "https") hostedProbeClient else probeClient
            client.newCall(request).execute().use { response ->
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
                    if (probe(host, port, "http")) {
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

    private data class SavedConnection(val host: String, val port: Int, val scheme: String)

    private suspend fun readSaved(): SavedConnection? {
        val preferences = appContext.backendConnectionDataStore.data.first()
        val host = preferences[Keys.Host] ?: return null
        val port = preferences[Keys.Port] ?: DEFAULT_PORT
        // Missing scheme means the value was saved before hosted mode existed - it was always LAN.
        val scheme = preferences[Keys.Scheme] ?: "http"
        return SavedConnection(host, port, scheme)
    }

    private suspend fun persistSaved(host: String, port: Int, scheme: String) {
        appContext.backendConnectionDataStore.edit { preferences ->
            preferences[Keys.Host] = host
            preferences[Keys.Port] = port
            preferences[Keys.Scheme] = scheme
        }
    }

    private object Keys {
        val Host = stringPreferencesKey("backend_host")
        val Port = intPreferencesKey("backend_port")
        val Scheme = stringPreferencesKey("backend_scheme")
    }

    companion object {
        const val DEFAULT_PORT = 8000

        /** Single source of truth for the hosted VPS API - see CLAUDE.md "Hosted Android Networking". */
        const val HOSTED_HOST = "api.museuma7k9x3.tech"
        const val HOSTED_PORT = 443

        private const val PROBE_TIMEOUT_MS = 1200L
        private const val HOSTED_PROBE_TIMEOUT_MS = 8000L
        private const val MAX_CONCURRENT_PROBES = 24
    }
}
