package com.example.museumapp.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

class NetworkErrorMessagesTest {
    private val lanBaseUrl = "http://192.168.100.12:8000/"
    private val adbReverseBaseUrl = "http://127.0.0.1:8000/"

    @Test
    fun mapsConnectionRefusedForLanDebugBuilds() {
        val message = NetworkErrorMessages.from(
            ConnectException("Connection refused"),
            baseUrl = lanBaseUrl,
            debug = true
        )

        assertTrue(message.contains(lanBaseUrl))
        assertTrue(message.contains("same Wi-Fi"))
        assertTrue(message.contains("Windows Firewall"))
    }

    @Test
    fun mapsConnectionRefusedForAdbReverseDebugBuilds() {
        val message = NetworkErrorMessages.from(
            ConnectException("Connection refused"),
            baseUrl = adbReverseBaseUrl,
            debug = true
        )

        assertTrue(message.contains(adbReverseBaseUrl))
        assertTrue(message.contains("adb reverse tcp:8000 tcp:8000"))
        assertTrue(message.contains("adb devices"))
    }

    @Test
    fun mapsUnknownHostForDebugBuilds() {
        val message = NetworkErrorMessages.from(
            UnknownHostException("bad-host"),
            baseUrl = lanBaseUrl,
            debug = true
        )

        assertTrue(message.contains("Could not resolve"))
        assertTrue(message.contains("API_BASE_URL"))
    }

    @Test
    fun mapsConnectTimeoutSeparatelyFromReadTimeout() {
        val connectMessage = NetworkErrorMessages.from(
            SocketTimeoutException("connect timed out"),
            baseUrl = lanBaseUrl,
            debug = true
        )
        val readMessage = NetworkErrorMessages.from(
            SocketTimeoutException("Read timed out"),
            baseUrl = lanBaseUrl,
            debug = true
        )

        assertTrue(connectMessage.startsWith("Connection timed out"))
        assertTrue(connectMessage.contains(lanBaseUrl))
        assertTrue(readMessage.startsWith("Read timeout"))
    }

    @Test
    fun mapsCleartextPolicyFailure() {
        val message = NetworkErrorMessages.from(
            UnknownServiceException("CLEARTEXT communication to 192.168.1.20 not permitted"),
            baseUrl = lanBaseUrl,
            debug = true
        )

        assertTrue(message.contains("Android blocked"))
        assertTrue(message.contains("network security"))
    }

    @Test
    fun mapsNoNetworkRoute() {
        val message = NetworkErrorMessages.from(
            NoRouteToHostException("No route to host"),
            baseUrl = lanBaseUrl,
            debug = true
        )

        assertTrue(message.contains("No network route"))
    }

    @Test
    fun releaseMessagesDoNotIncludeBackendUrl() {
        val message = NetworkErrorMessages.from(
            IOException("boom"),
            baseUrl = lanBaseUrl,
            debug = false
        )

        assertEquals("Could not connect to the backend.", message)
    }
}
