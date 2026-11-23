package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.TokenManager
import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedAuthBridgeTest {

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val authRetry: String?,
        val body: String
    )

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var tokenManager: TokenManager
    private val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val queuedResponses = ArrayDeque<Pair<Int, String>>()

    @Before
    fun setUp() {
        recordedRequests.clear()
        queuedResponses.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val reqBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            recordedRequests.add(
                RecordedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    authRetry = exchange.requestHeaders.getFirst(BackendRepository.HEADER_AUTH_RETRY),
                    body = reqBody
                )
            )
            val (code, body) = synchronized(queuedResponses) {
                if (queuedResponses.isEmpty()) 500 to """{"error":"No queued response"}"""
                else queuedResponses.removeFirst()
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/api/"
        val context = ApplicationProvider.getApplicationContext<Context>()
        tokenManager = TokenManager(context)
        tokenManager.clearToken()
    }

    @After
    fun tearDown() {
        tokenManager.clearToken()
        server.stop(0)
    }

    private fun enqueue(code: Int, jsonBody: String) {
        synchronized(queuedResponses) {
            queuedResponses.addLast(code to jsonBody)
        }
    }

    @Test
    fun exchangeFirebaseIdToken_savesExpressJwtAndParsesLinkedAccountMetadata() = runBlocking {
        enqueue(
            200,
            """
            {
              "token": "express-jwt-123",
              "accountLinked": true,
              "user": {
                "id": "pg-user-42",
                "name": "Rekha Devaraj",
                "email": "rekha@example.com",
                "role": "Creator",
                "firebaseUid": "fb-uid-999",
                "authProviders": ["password", "google"],
                "emailVerified": true
              }
            }
            """.trimIndent()
        )

        val repo = BackendRepository(
            tokenManager = tokenManager,
            baseUrl = baseUrl
        )

        val result = repo.exchangeFirebaseIdToken("firebase-id-token-xyz")

        assertThat(recordedRequests).hasSize(1)
        assertThat(recordedRequests[0].path).isEqualTo("/api/auth/firebase-exchange")
        assertThat(recordedRequests[0].body).contains("firebase-id-token-xyz")
        assertThat(tokenManager.getToken()).isEqualTo("express-jwt-123")
        assertThat(result.accountLinked).isTrue()
        assertThat(result.user.id).isEqualTo("pg-user-42")
        assertThat(result.user.firebaseUid).isEqualTo("fb-uid-999")
        assertThat(result.user.authProviders).containsExactly("password", "google")
        assertThat(result.user.emailVerified).isTrue()
    }

    @Test
    fun loginWithExchange_andRequestCustomToken_returnBothExpressJwtAndFirebaseCustomToken() = runBlocking {
        enqueue(
            200,
            """
            {
              "token": "express-jwt-login",
              "firebaseCustomToken": "fb-custom-token-abc",
              "user": {
                "id": "pg-user-7",
                "name": "Aarav",
                "email": "aarav@example.com",
                "firebaseUid": "i2i_pg-user-7",
                "authProviders": ["password"]
              }
            }
            """.trimIndent()
        )
        enqueue(200, """{"firebaseCustomToken":"fb-custom-token-refreshed"}""")

        val repo = BackendRepository(
            tokenManager = tokenManager,
            baseUrl = baseUrl
        )

        val loginResult = repo.loginWithExchange("aarav@example.com", "secret123")
        assertThat(loginResult.token).isEqualTo("express-jwt-login")
        assertThat(loginResult.firebaseCustomToken).isEqualTo("fb-custom-token-abc")
        assertThat(loginResult.user.firebaseUid).isEqualTo("i2i_pg-user-7")
        assertThat(tokenManager.getToken()).isEqualTo("express-jwt-login")

        val refreshedCustom = repo.requestFirebaseCustomToken()
        assertThat(recordedRequests).hasSize(2)
        assertThat(recordedRequests[0].path).isEqualTo("/api/auth/login")
        assertThat(recordedRequests[1].path).isEqualTo("/api/auth/firebase-custom-token")
        assertThat(recordedRequests[1].authorization).isEqualTo("Bearer express-jwt-login")
        assertThat(refreshedCustom).isEqualTo("fb-custom-token-refreshed")
    }

    @Test
    fun protected401Response_triggersSingleFlightFirebaseExchangeAndRetriesOnce() = runBlocking {
        tokenManager.saveToken("expired-jwt-old")
        val refreshCalls = AtomicInteger(0)

        // 1. Initial GET /auth/me returns 401
        enqueue(401, """{"error":"Token expired"}""")
        // 2. Authenticator calls POST /auth/firebase-exchange and gets fresh Express JWT
        enqueue(
            200,
            """{"token":"fresh-jwt-new","user":{"id":"pg-user-1","name":"Unified User","email":"u@example.com"}}"""
        )
        // 3. Retried GET /auth/me succeeds with new Bearer token
        enqueue(
            200,
            """
            {
              "user": {
                "id": "pg-user-1",
                "name": "Unified User",
                "email": "u@example.com",
                "firebaseUid": "fb-1",
                "authProviders": ["google"]
              }
            }
            """.trimIndent()
        )

        val repo = BackendRepository(
            tokenManager = tokenManager,
            baseUrl = baseUrl,
            firebaseIdTokenProvider = { forceRefresh ->
                assertThat(forceRefresh).isTrue()
                refreshCalls.incrementAndGet()
                "fresh-firebase-id-token"
            }
        )

        val user = repo.fetchCurrentUser()

        assertThat(user).isNotNull()
        assertThat(user!!.id).isEqualTo("pg-user-1")
        assertThat(user.firebaseUid).isEqualTo("fb-1")
        assertThat(tokenManager.getToken()).isEqualTo("fresh-jwt-new")
        assertThat(refreshCalls.get()).isEqualTo(1)

        assertThat(recordedRequests).hasSize(3)
        assertThat(recordedRequests[0].path).isEqualTo("/api/auth/me")
        assertThat(recordedRequests[0].authorization).isEqualTo("Bearer expired-jwt-old")
        assertThat(recordedRequests[1].path).isEqualTo("/api/auth/firebase-exchange")
        assertThat(recordedRequests[2].path).isEqualTo("/api/auth/me")
        assertThat(recordedRequests[2].authorization).isEqualTo("Bearer fresh-jwt-new")
        assertThat(recordedRequests[2].authRetry).isEqualTo("1")
    }
}
