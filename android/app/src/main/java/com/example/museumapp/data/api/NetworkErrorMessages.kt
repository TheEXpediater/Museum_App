package com.example.museumapp.data.api

import com.example.museumapp.BuildConfig
import java.io.IOException
import java.net.InetAddress
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.net.URI

object NetworkErrorMessages {
    fun from(
        exception: IOException,
        baseUrl: String = BuildConfig.API_BASE_URL,
        debug: Boolean = BuildConfig.DEBUG
    ): String {
        val message = exception.message.orEmpty()
        return when {
            exception.isCleartextBlocked() -> {
                if (debug) {
                    "Android blocked the local HTTP connection to $baseUrl. Check the debug network security configuration."
                } else {
                    "Could not establish a secure connection to the backend."
                }
            }
            exception is UnknownHostException -> {
                if (debug) {
                    "Could not resolve the backend host in $baseUrl. Check API_BASE_URL for typos."
                } else {
                    "Could not connect to the backend."
                }
            }
            exception is NoRouteToHostException || exception.isNoNetworkRoute(message) -> {
                if (debug) {
                    "No network route to $baseUrl. ${connectionHint(baseUrl)}"
                } else {
                    "Could not reach the backend network."
                }
            }
            exception is ConnectException -> {
                if (debug) {
                    couldNotReach(baseUrl)
                } else {
                    "Could not connect to the backend."
                }
            }
            exception is SocketTimeoutException && message.contains("connect", ignoreCase = true) -> {
                if (debug) {
                    "Connection timed out while contacting $baseUrl. ${connectionHint(baseUrl)}"
                } else {
                    "Connection timed out while contacting the backend."
                }
            }
            exception is SocketTimeoutException -> {
                if (debug) {
                    "Read timeout while waiting for $baseUrl. The backend accepted the request but did not respond in time."
                } else {
                    "The backend did not respond in time."
                }
            }
            else -> {
                if (debug) {
                    couldNotReach(baseUrl)
                } else {
                    "Could not connect to the backend."
                }
            }
        }
    }

    private fun couldNotReach(baseUrl: String): String =
        "Could not reach $baseUrl. ${connectionHint(baseUrl)}"

    private fun connectionHint(baseUrl: String): String {
        return when (backendMode(baseUrl)) {
            BackendMode.AdbReverse -> "Run adb reverse tcp:8000 tcp:8000 and verify the phone is listed by adb devices."
            BackendMode.Lan -> "Confirm that the phone and computer are on the same Wi-Fi and that TCP port 8000 is allowed through Windows Firewall."
            BackendMode.Emulator -> "10.0.2.2 is only for the Android emulator; use the Windows LAN IP for a physical phone."
            BackendMode.Other -> "Confirm that FastAPI is running with --host 0.0.0.0 and that the configured backend address is reachable."
        }
    }

    private fun backendMode(baseUrl: String): BackendMode {
        val host = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")
        return when {
            host == "127.0.0.1" || host.equals("localhost", ignoreCase = true) -> BackendMode.AdbReverse
            host == "10.0.2.2" -> BackendMode.Emulator
            host.isPrivateLanIpv4() -> BackendMode.Lan
            else -> BackendMode.Other
        }
    }

    private fun IOException.isCleartextBlocked(): Boolean =
        this is UnknownServiceException && message.orEmpty().contains("CLEARTEXT", ignoreCase = true)

    private fun IOException.isNoNetworkRoute(message: String): Boolean =
        this is SocketException &&
            listOf("No route to host", "Network is unreachable", "ENETUNREACH", "EHOSTUNREACH").any {
                message.contains(it, ignoreCase = true)
            }

    private fun String.isPrivateLanIpv4(): Boolean {
        val bytes = runCatching { InetAddress.getByName(this).address }.getOrNull() ?: return false
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)
    }

    private enum class BackendMode {
        AdbReverse,
        Lan,
        Emulator,
        Other
    }
}
