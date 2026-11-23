package com.example.data.repository

import android.util.Log
import com.example.data.local.InnovationDao
import com.example.data.local.SearchHistoryDao
import com.example.data.local.toCachedEntity
import com.example.data.local.toDomain
import com.example.data.model.AuthUser
import com.example.data.model.BackendComment
import com.example.data.model.BackendInnovation
import com.example.data.model.BackendProposal
import com.example.data.model.BackendTalent
import com.example.data.model.CategoryItem
import com.example.data.model.DashboardStats
import com.example.data.model.ExactCategories
import com.example.data.model.PitchDeckResult
import com.example.data.model.PitchSlide
import com.example.data.network.ExponentialBackoffRetryInterceptor
import com.example.data.network.Idea2ImpactApiService
import com.example.data.network.RetryPolicyConfig
import com.example.data.preferences.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import com.example.data.model.AuthExchangeResult
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class BackendRepository(
    private val tokenManager: TokenManager,
    private val innovationDao: InnovationDao? = null,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val baseUrl: String = BASE_URL,
    private val firebaseIdTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null
) {
    private val json get() = sharedJson

    @Volatile
    private var backendAwake = false
    private val warmUpMutex = Mutex()
    private val tokenRefreshLock = Any()

    private val api: Idea2ImpactApiService by lazy {
        val logging = HttpLoggingInterceptor { msg ->
            Log.d(TAG, SanaPromptEngine.redactSensitiveInfo(msg))
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
        }

        val retryInterceptor = ExponentialBackoffRetryInterceptor(
            config = RetryPolicyConfig(
                maxRetries = 3,
                initialDelayMs = 1_000L,
                maxDelayMs = 12_000L,
                backoffMultiplier = 2.0,
                jitterRatio = 0.25
            ),
            logger = { msg -> Log.w(TAG, msg) }
        )

        val client = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(80, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(95, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val token = tokenManager.getToken()
                val reqBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                if (!token.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(reqBuilder.build())
            }
            .authenticator(createTokenRefreshAuthenticator())
            .addInterceptor(retryInterceptor)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(Idea2ImpactApiService::class.java)
    }

    private val exchangeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private fun createTokenRefreshAuthenticator(): Authenticator = Authenticator { _: Route?, response: okhttp3.Response ->
        val provider = firebaseIdTokenProvider ?: return@Authenticator null
        val path = response.request.url.encodedPath
        // Never intercept auth entry endpoints or repeat a previously retried 401
        if (path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/firebase-exchange") ||
            response.request.header(HEADER_AUTH_RETRY) != null
        ) {
            return@Authenticator null
        }

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        synchronized(tokenRefreshLock) {
            val currentToken = tokenManager.getToken()
            // Another in-flight request already refreshed the JWT — reuse it immediately
            if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .header(HEADER_AUTH_RETRY, "1")
                    .build()
            }

            val refreshedJwt = runCatching {
                val idToken = runBlocking { provider(true) }?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val payload = buildJsonObject {
                    put("idToken", idToken)
                }.toString()
                val exchangeUrl = baseUrl.trimEnd('/') + "/auth/firebase-exchange"
                val req = Request.Builder()
                    .url(exchangeUrl)
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                exchangeClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val raw = res.body?.string().orEmpty()
                    if (raw.isBlank()) return@use null
                    val obj = json.parseToJsonElement(raw).asObjOrEmpty()
                    val newToken = obj.str("token")
                        ?: obj.obj("data")?.str("token")
                        ?: obj.str("accessToken")
                    if (!newToken.isNullOrBlank()) {
                        tokenManager.saveToken(newToken)
                    }
                    newToken
                }
            }.onFailure { e ->
                Log.w(TAG, "Silent 401 JWT refresh failed: ${SanaPromptEngine.redactSensitiveInfo(e.message.orEmpty())}")
            }.getOrNull()

            if (refreshedJwt.isNullOrBlank()) {
                return@Authenticator null
            }

            response.request.newBuilder()
                .header("Authorization", "Bearer $refreshedJwt")
                .header(HEADER_AUTH_RETRY, "1")
                .build()
        }
    }

    private suspend fun <T> executeWithColdStartRetry(
        retryOnTimeout: Boolean = true,
        maxAttempts: Int = 1,
        block: suspend () -> T
    ): T {
        if (!backendAwake) {
            return warmUpMutex.withLock {
                runWithRetry(retryOnTimeout = retryOnTimeout, maxAttempts = maxAttempts, block = block)
            }
        }
        return runWithRetry(retryOnTimeout = retryOnTimeout, maxAttempts = maxAttempts, block = block)
    }

    private suspend fun <T> runWithRetry(
        retryOnTimeout: Boolean,
        maxAttempts: Int,
        block: suspend () -> T
    ): T {
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                val result = block()
                backendAwake = true
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendApiException) {
                if (e.code in RETRYABLE_HTTP_CODES && attempt < maxAttempts) {
                    Log.w(TAG, "Server warming up (HTTP ${e.code}), retrying attempt ${attempt + 1}/$maxAttempts")
                    lastError = e
                    delay(1500L * attempt)
                    continue
                }
                backendAwake = true
                throw e
            } catch (e: InterruptedIOException) {
                lastError = e
                if (retryOnTimeout && attempt < maxAttempts) {
                    Log.w(TAG, "Request timed out (${e.javaClass.simpleName}), retrying attempt ${attempt + 1}/$maxAttempts")
                    delay(1500L * attempt)
                    continue
                }
                throw e
            } catch (e: IOException) {
                lastError = e
                if (retryOnTimeout && attempt < maxAttempts) {
                    Log.w(TAG, "Transient network error (${e.javaClass.simpleName}), retrying attempt ${attempt + 1}/$maxAttempts")
                    delay(1500L * attempt)
                    continue
                }
                throw e
            }
        }
        throw lastError ?: SocketTimeoutException("Request timed out")
    }

    private fun JsonObject.toRequestBody() =
        toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun parseRoot(response: Response<ResponseBody>): JsonElement {
        val raw = if (response.isSuccessful) {
            response.body()?.string().orEmpty()
        } else {
            val err = response.errorBody()?.string().orEmpty()
            val msg = extractErrorMessage(err)
                ?: "Request failed (${response.code()} ${response.message()})"
            throw BackendApiException(response.code(), SanaPromptEngine.redactSensitiveInfo(msg))
        }
        if (raw.isBlank()) return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(raw)
        } catch (e: SerializationException) {
            throw BackendApiException(
                code = response.code().takeIf { it > 0 } ?: 502,
                message = "The backend returned an unreadable non-JSON response (HTTP ${response.code()}). It may be waking up or temporarily unavailable."
            )
        }
    }

    private fun extractErrorMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            obj.str("error") ?: obj.str("message") ?: obj.str("detail")
        }.getOrNull()
    }

    // --- Auth ---

    suspend fun login(email: String, password: String): AuthUser =
        loginWithExchange(email, password).user

    suspend fun loginWithExchange(email: String, password: String): AuthExchangeResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toRequestBody()
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.login(payload))
        }
        parseAuthExchangeResult(root)
    }

    suspend fun register(name: String, email: String, password: String): AuthUser =
        registerWithExchange(name, email, password).user

    suspend fun registerWithExchange(name: String, email: String, password: String): AuthExchangeResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("name", name.trim())
            put("email", email.trim())
            put("password", password)
        }.toRequestBody()
        val root = executeWithColdStartRetry(retryOnTimeout = false) {
            parseRoot(api.register(payload))
        }
        parseAuthExchangeResult(root)
    }

    suspend fun exchangeFirebaseIdToken(idToken: String): AuthExchangeResult = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("idToken", idToken.trim())
        }.toRequestBody()
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.exchangeFirebaseToken(payload))
        }
        parseAuthExchangeResult(root)
    }

    suspend fun requestFirebaseCustomToken(): String? = withContext(Dispatchers.IO) {
        if (tokenManager.getToken().isNullOrBlank()) return@withContext null
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.requestFirebaseCustomToken())
        }
        val obj = root.asObjOrEmpty()
        obj.str("firebaseCustomToken")
            ?: obj.str("customToken")
            ?: obj.obj("data")?.str("firebaseCustomToken")
            ?: obj.obj("data")?.str("customToken")
    }

    private fun parseAuthExchangeResult(root: JsonElement): AuthExchangeResult {
        val obj = root.asObjOrEmpty()
        val token = obj.str("token")
            ?: obj.obj("data")?.str("token")
            ?: obj.str("accessToken")
        if (!token.isNullOrBlank()) {
            tokenManager.saveToken(token)
        }
        val firebaseCustomToken = obj.str("firebaseCustomToken")
            ?: obj.str("customToken")
            ?: obj.obj("data")?.str("firebaseCustomToken")
            ?: obj.obj("data")?.str("customToken")
        val accountLinked = obj.boolVal("accountLinked")
            ?: obj.boolVal("linked")
            ?: obj.obj("data")?.boolVal("accountLinked")
            ?: obj.obj("data")?.boolVal("linked")
            ?: false
        val userObj = obj.obj("user") ?: obj.obj("data")?.obj("user") ?: obj.obj("data") ?: obj
        return AuthExchangeResult(
            user = parseAuthUser(userObj),
            token = token,
            firebaseCustomToken = firebaseCustomToken,
            accountLinked = accountLinked
        )
    }

    suspend fun fetchCurrentUser(): AuthUser? = withContext(Dispatchers.IO) {
        if (tokenManager.getToken().isNullOrBlank()) return@withContext null
        try {
            val root = executeWithColdStartRetry(retryOnTimeout = true) {
                parseRoot(api.getMe())
            }
            val obj = root.asObjOrEmpty()
            val userObj = obj.obj("user") ?: obj.obj("profile") ?: obj.obj("data") ?: obj
            parseAuthUser(userObj)
        } catch (e: BackendApiException) {
            if (e.code == 401 || e.code == 403) {
                tokenManager.clearToken()
            }
            null
        }
    }

    fun signOut() {
        tokenManager.clearToken()
    }

    fun hasSavedToken(): Boolean = !tokenManager.getToken().isNullOrBlank()

    // --- Categories ---

    fun observeCachedCategories(): Flow<List<CategoryItem>> {
        val dao = innovationDao ?: return flowOf(ExactCategories.defaultItems)
        return dao.observeCategories().map { cached ->
            if (cached.isEmpty()) ExactCategories.defaultItems
            else cached.map { it.toDomain() }
        }
    }

    suspend fun fetchCategories(): List<CategoryItem> = withContext(Dispatchers.IO) {
        val defaults = ExactCategories.defaultItems
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.getCategories())
        }
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root.arr("categories") ?: root.arr("data") ?: root.arr("items") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        if (array.isEmpty()) return@withContext defaults

        val countsByName = mutableMapOf<String, Int>()
        array.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val name = (obj.str("name") ?: obj.str("title") ?: obj.str("label")).orEmpty()
            val count = obj.intVal("count")
                ?: obj.obj("_count")?.intVal("innovations")
                ?: obj.intVal("innovationCount")
                ?: 5
            if (name.isNotBlank()) countsByName[name.lowercase()] = count
        }

        val resolved = defaults.map { item ->
            val liveCount = countsByName[item.name.lowercase()] ?: item.count
            item.copy(count = liveCount)
        }
        val now = System.currentTimeMillis()
        innovationDao?.replaceCategories(
            resolved.mapIndexed { idx, item -> item.toCachedEntity(sortOrder = idx, cachedAt = now) }
        )
        resolved
    }

    // --- Local Marketplace Search History ---

    fun observeRecentSearches(limit: Int = MAX_RECENT_SEARCHES): Flow<List<String>> {
        val dao = searchHistoryDao ?: return flowOf(emptyList())
        return dao.observeRecentSearches(limit).map { entries -> entries.map { it.query } }
    }

    suspend fun recordMarketplaceSearch(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext
        searchHistoryDao?.recordSearch(
            rawQuery = clean,
            searchedAt = System.currentTimeMillis(),
            maxEntries = MAX_RECENT_SEARCHES
        )
    }

    suspend fun deleteRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return@withContext
        searchHistoryDao?.deleteByNormalizedQuery(normalized)
    }

    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        searchHistoryDao?.clearAllSearches()
    }

    // --- Innovations ---

    fun observeCachedInnovations(
        search: String = "",
        category: String = "",
        stage: String = ""
    ): Flow<List<BackendInnovation>> {
        val dao = innovationDao ?: return flowOf(emptyList())
        val catParam = if (category == "All" || category.isBlank()) "" else category.trim()
        val stageParam = if (stage == "All" || stage.isBlank()) "" else stage.trim()
        return dao.observeFilteredInnovations(
            search = search.trim(),
            category = catParam,
            stage = stageParam
        ).map { list -> list.map { it.toDomain() } }
    }

    suspend fun hasCachedInnovations(): Boolean = withContext(Dispatchers.IO) {
        (innovationDao?.getInnovationCount() ?: 0) > 0
    }

    suspend fun fetchInnovations(
        search: String = "",
        category: String = "",
        stage: String = ""
    ): List<BackendInnovation> = withContext(Dispatchers.IO) {
        val catParam = if (category == "All" || category.isBlank()) "" else category
        val stageParam = if (stage == "All" || stage.isBlank()) "" else stage
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(
                api.getInnovations(
                    search = search.trim(),
                    category = catParam,
                    stage = stageParam
                )
            )
        }
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root.arr("innovations")
                ?: root.arr("items")
                ?: root.arr("data")
                ?: root.obj("data")?.arr("innovations")
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val items = array.mapNotNull { (it as? JsonObject)?.let(::parseInnovation) }
        cacheFetchedInnovations(
            items = items,
            isFullCatalogQuery = search.isBlank() && catParam.isBlank() && stageParam.isBlank()
        )
        items
    }

    private suspend fun cacheFetchedInnovations(
        items: List<BackendInnovation>,
        isFullCatalogQuery: Boolean
    ) {
        val dao = innovationDao ?: return
        val now = System.currentTimeMillis()
        if (isFullCatalogQuery) {
            val entities = items.mapIndexed { idx, item ->
                item.toCachedEntity(sortOrder = idx, cachedAt = now)
            }
            dao.replaceAllInnovations(entities)
        } else if (items.isNotEmpty()) {
            val entities = items.mapIndexed { idx, item ->
                val existingOrder = dao.getInnovationByIdOnce(item.id)?.sortOrder ?: (1000 + idx)
                item.toCachedEntity(sortOrder = existingOrder, cachedAt = now)
            }
            dao.upsertInnovations(entities)
        }
    }

    suspend fun fetchInnovationDetail(id: String): Pair<BackendInnovation?, List<BackendComment>> =
        withContext(Dispatchers.IO) {
            val root = executeWithColdStartRetry(retryOnTimeout = true) {
                parseRoot(api.getInnovationDetail(id))
            }
            val obj = root.asObjOrEmpty()
            val innObj = obj.obj("innovation") ?: obj.obj("data")?.obj("innovation") ?: obj.obj("data") ?: obj
            val innovation = if (innObj.containsKey("title") || innObj.containsKey("id") || innObj.containsKey("_id")) {
                parseInnovation(innObj)
            } else null

            if (innovation != null) {
                val dao = innovationDao
                if (dao != null) {
                    val existingOrder = dao.getInnovationByIdOnce(innovation.id)?.sortOrder ?: 0
                    dao.upsertInnovation(innovation.toCachedEntity(sortOrder = existingOrder))
                }
            }

            val commentArray = obj.arr("comments")
                ?: innObj.arr("comments")
                ?: obj.obj("data")?.arr("comments")
                ?: JsonArray(emptyList())
            val comments = commentArray.mapNotNull { (it as? JsonObject)?.let { c -> parseComment(c, id) } }
            innovation to comments
        }

    suspend fun cacheLikeState(id: String, isLiked: Boolean, likeCount: Int) = withContext(Dispatchers.IO) {
        innovationDao?.updateLikeState(id, isLiked, likeCount)
    }

    suspend fun cacheBookmarkState(id: String, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        innovationDao?.updateBookmarkState(id, isBookmarked)
    }

    suspend fun toggleLike(id: String): Pair<Boolean?, Int?> = withContext(Dispatchers.IO) {
        val root = parseRoot(api.toggleLike(id))
        val obj = root.asObjOrEmpty()
        val dataObj = obj.obj("data") ?: obj
        val liked = dataObj.boolVal("liked") ?: dataObj.boolVal("isLiked")
        val count = dataObj.intVal("likeCount") ?: dataObj.intVal("likesCount") ?: dataObj.intVal("likes")
        if (liked != null && count != null) {
            innovationDao?.updateLikeState(id, liked, count)
        }
        liked to count
    }

    suspend fun toggleFavourite(id: String): Boolean? = withContext(Dispatchers.IO) {
        val root = parseRoot(api.toggleFavourite(id))
        val obj = root.asObjOrEmpty()
        val dataObj = obj.obj("data") ?: obj
        val bookmarked = dataObj.boolVal("favourited")
            ?: dataObj.boolVal("favorited")
            ?: dataObj.boolVal("bookmarked")
            ?: dataObj.boolVal("saved")
        if (bookmarked != null) {
            innovationDao?.updateBookmarkState(id, bookmarked)
        }
        bookmarked
    }

    suspend fun postComment(innovationId: String, text: String): BackendComment? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("content", text.trim())
            put("body", text.trim())
            put("text", text.trim())
        }.toRequestBody()
        val root = parseRoot(api.postComment(innovationId, payload))
        val obj = root.asObjOrEmpty()
        val commentObj = obj.obj("comment") ?: obj.obj("data") ?: obj
        parseComment(commentObj, innovationId)
    }

    suspend fun submitProposal(
        innovationId: String,
        type: String,
        amount: String,
        message: String,
        contactInfo: String
    ): BackendProposal = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", type)
            put("proposalType", type)
            put("amount", amount.trim())
            put("message", message.trim())
            put("contactInfo", contactInfo.trim())
        }.toRequestBody()
        val root = parseRoot(api.submitProposal(innovationId, payload))
        val obj = root.asObjOrEmpty()
        val propObj = obj.obj("proposal") ?: obj.obj("data") ?: obj
        parseProposal(propObj, innovationId, type, amount, message, contactInfo)
    }

    suspend fun generatePitchDeck(innovation: BackendInnovation): PitchDeckResult = withContext(Dispatchers.IO) {
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.generatePitchDeck(innovation.id))
        }
        val obj = root.asObjOrEmpty()
        val slidesArr = obj.arr("slides")
            ?: obj.obj("pitchDeck")?.arr("slides")
            ?: obj.obj("data")?.arr("slides")
            ?: obj.arr("deck")

        if (slidesArr != null && slidesArr.isNotEmpty()) {
            val parsedSlides = slidesArr.mapIndexedNotNull { idx, el ->
                val sObj = el as? JsonObject ?: return@mapIndexedNotNull null
                val title = sObj.str("title") ?: sObj.str("heading") ?: "Slide ${idx + 1}"
                val subtitle = sObj.str("subtitle") ?: sObj.str("summary") ?: sObj.str("content") ?: ""
                val bullets = sObj.arr("bullets")?.mapNotNull { it.asStr() }
                    ?: sObj.arr("points")?.mapNotNull { it.asStr() }
                    ?: subtitle.split("\n").map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() }
                PitchSlide(
                    slideNumber = sObj.intVal("slideNumber") ?: (idx + 1),
                    title = title,
                    subtitle = subtitle,
                    bullets = if (bullets.isEmpty() && subtitle.isNotBlank()) listOf(subtitle) else bullets
                )
            }
            if (parsedSlides.isNotEmpty()) {
                return@withContext PitchDeckResult(innovation.title, parsedSlides.take(5))
            }
        }

        val summaryText = obj.str("summary")
            ?: obj.str("pitchDeck")
            ?: obj.str("content")
            ?: obj.str("text")
            ?: obj.obj("data")?.str("summary")
            ?: ""

        val defaultSlides = buildSlidesFromSummary(innovation, summaryText)
        PitchDeckResult(innovation.title, defaultSlides)
    }

    // --- Talents ---

    suspend fun fetchTalents(): List<BackendTalent> = withContext(Dispatchers.IO) {
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.getTalents())
        }
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root.arr("talents")
                ?: root.arr("users")
                ?: root.arr("builders")
                ?: root.arr("data")
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        array.mapNotNull { (it as? JsonObject)?.let(::parseTalent) }
    }

    // --- Dashboard Stats ---

    suspend fun fetchDashboardStats(): DashboardStats = withContext(Dispatchers.IO) {
        val root = executeWithColdStartRetry(retryOnTimeout = true) {
            parseRoot(api.getDashboardStats())
        }
        val obj = root.asObjOrEmpty()
        val statsObj = obj.obj("stats") ?: obj.obj("data") ?: obj

        val myArr = obj.arr("myInnovations")
            ?: statsObj.arr("myInnovations")
            ?: obj.arr("innovations")
            ?: JsonArray(emptyList())
        val savedArr = obj.arr("saved")
            ?: obj.arr("favourites")
            ?: obj.arr("favorites")
            ?: statsObj.arr("savedInnovations")
            ?: JsonArray(emptyList())
        val propArr = obj.arr("proposals")
            ?: obj.arr("inboundProposals")
            ?: statsObj.arr("proposals")
            ?: JsonArray(emptyList())

        val myList = myArr.mapNotNull { (it as? JsonObject)?.let(::parseInnovation) }
        val savedList = savedArr.mapNotNull { (it as? JsonObject)?.let(::parseInnovation) }
        val propList = propArr.mapNotNull { (it as? JsonObject)?.let { p -> parseProposal(p) } }

        val posted = statsObj.intVal("innovationsPosted")
            ?: statsObj.intVal("innovationsCount")
            ?: statsObj.intVal("totalInnovations")
            ?: myList.size
        val saved = statsObj.intVal("savedOpportunities")
            ?: statsObj.intVal("savedCount")
            ?: statsObj.intVal("favouritesCount")
            ?: savedList.size
        val proposals = statsObj.intVal("proposalsReceived")
            ?: statsObj.intVal("proposalsCount")
            ?: propList.size
        val likes = statsObj.intVal("totalUpvotes")
            ?: statsObj.intVal("totalLikes")
            ?: statsObj.intVal("likesCount")
            ?: myList.sumOf { it.likeCount }

        DashboardStats(
            innovationsPosted = posted,
            savedOpportunities = saved,
            proposalsReceived = proposals,
            totalUpvotes = likes,
            myInnovations = myList,
            savedList = savedList,
            inboundProposals = propList
        )
    }

    // --- AI Copilot ---

    suspend fun sendAiChat(
        prompt: String,
        context: String = "",
        customApiKey: String? = tokenManager.getCustomApiKey(),
        useFastModel: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = validateCustomApiKey(customApiKey)

        val payload = buildJsonObject {
            put("prompt", prompt.trim())
            put("context", context.trim())
            put("fastMode", useFastModel)
            if (!cleanKey.isNullOrEmpty()) {
                put("apiKey", cleanKey)
            }
        }.toRequestBody()

        val root = withTimeout(55_000L) {
            executeWithColdStartRetry(retryOnTimeout = true) {
                parseRoot(api.aiChat(payload))
            }
        }
        extractAiChatReply(root)
    }

    // --- JSON Parsers ---

    private fun parseAuthUser(obj: JsonObject): AuthUser {
        val id = obj.str("id") ?: obj.str("_id") ?: obj.str("userId") ?: ""
        val name = obj.str("name") ?: obj.str("fullName") ?: "Innovator"
        val email = obj.str("email") ?: ""
        val role = obj.str("role") ?: "Creator"
        val headline = obj.str("headline") ?: obj.str("title") ?: "Verified Innovator"
        val bio = obj.str("bio") ?: ""
        val avatarUrl = obj.str("avatarUrl") ?: obj.str("avatar") ?: obj.str("image")
        val location = obj.str("location") ?: "Global"
        val skills = obj.arr("skills")?.mapNotNull { it.asStr() }.orEmpty()
        val verified = obj.boolVal("verified") ?: true
        val firebaseUid = obj.str("firebaseUid") ?: obj.str("firebase_uid")
        val authProviders = (obj.arr("authProviders") ?: obj.arr("auth_providers") ?: obj.arr("providers"))
            ?.mapNotNull { it.asStr()?.lowercase() }
            ?.distinct()
            .orEmpty()
        val emailVerified = obj.boolVal("emailVerified") ?: obj.boolVal("email_verified") ?: verified
        return AuthUser(
            id = id,
            name = name,
            email = email,
            role = role,
            headline = headline,
            bio = bio,
            avatarUrl = avatarUrl,
            location = location,
            skills = skills,
            verified = verified,
            firebaseUid = firebaseUid,
            authProviders = authProviders,
            emailVerified = emailVerified
        )
    }

    private fun parseInnovation(obj: JsonObject): BackendInnovation {
        val id = obj.str("id") ?: obj.str("_id") ?: ""
        val creator = obj.obj("creator") ?: obj.obj("author") ?: obj.obj("user")
        val countObj = obj.obj("_count")

        val category = obj.str("category")
            ?: obj.obj("categoryObj")?.str("name")
            ?: "Ideas"

        val stage = obj.str("stage")
            ?: when (obj.intVal("stageIndex")) {
                0 -> "Idea"
                1 -> "Prototype"
                2 -> "MVP"
                3 -> "Launched"
                else -> "Prototype"
            }

        val fundingGoal = obj.doubleVal("fundingGoal")
            ?: obj.doubleVal("funding_goal")
            ?: obj.doubleVal("price")
            ?: obj.doubleVal("targetFunding")
            ?: 50000.0
        val valuation = obj.doubleVal("valuation")
            ?: obj.doubleVal("estimatedValuation")
            ?: (fundingGoal * 6.0).coerceAtLeast(250000.0)

        val likeCount = obj.intVal("likeCount")
            ?: obj.intVal("likesCount")
            ?: obj.intVal("likes")
            ?: countObj?.intVal("likes")
            ?: 0

        val commentCount = obj.intVal("commentCount")
            ?: obj.intVal("commentsCount")
            ?: countObj?.intVal("comments")
            ?: 0

        return BackendInnovation(
            id = id,
            title = obj.str("title") ?: "Untitled Innovation",
            description = obj.str("description") ?: obj.str("summary") ?: "",
            category = category,
            stage = stage,
            fundingGoal = fundingGoal,
            valuation = valuation,
            imageUrl = obj.str("imageUrl") ?: obj.str("thumbnail") ?: obj.str("image") ?: obj.str("coverImage"),
            authorId = obj.str("creatorId") ?: obj.str("authorId") ?: creator?.str("id") ?: "",
            authorName = obj.str("authorName") ?: creator?.str("name") ?: "Verified Creator",
            authorTitle = obj.str("authorHeadline") ?: creator?.str("headline") ?: "Verified Creator",
            authorAvatar = obj.str("authorAvatar") ?: creator?.str("avatarUrl") ?: creator?.str("avatar"),
            isVerifiedCreator = obj.boolVal("verified") ?: creator?.boolVal("verified") ?: true,
            likeCount = likeCount,
            commentCount = commentCount,
            isLiked = obj.boolVal("isLiked") ?: obj.boolVal("liked") ?: false,
            isBookmarked = obj.boolVal("isFavourited") ?: obj.boolVal("isBookmarked") ?: obj.boolVal("saved") ?: false,
            ndaRequired = obj.boolVal("ndaRequired") ?: true,
            createdAt = obj.str("createdAt") ?: ""
        )
    }

    private fun parseComment(obj: JsonObject, fallbackInnovationId: String): BackendComment {
        val user = obj.obj("user") ?: obj.obj("author")
        return BackendComment(
            id = obj.str("id") ?: obj.str("_id") ?: "${System.nanoTime()}",
            innovationId = obj.str("innovationId") ?: fallbackInnovationId,
            authorName = obj.str("authorName") ?: user?.str("name") ?: "Innovator",
            authorAvatar = obj.str("authorAvatar") ?: user?.str("avatarUrl") ?: user?.str("avatar"),
            body = obj.str("content") ?: obj.str("body") ?: obj.str("text") ?: "",
            createdAt = obj.str("createdAt") ?: "Just now"
        )
    }

    private fun parseTalent(obj: JsonObject): BackendTalent {
        val rawRole = obj.str("role") ?: "Builder"
        val title = obj.str("headline") ?: obj.str("title") ?: rawRole
        val roleCat = when {
            rawRole.contains("Investor", ignoreCase = true) -> "Investors"
            rawRole.contains("Patent", ignoreCase = true) || rawRole.contains("IP", ignoreCase = true) -> "Patent Professionals"
            rawRole.contains("Creator", ignoreCase = true) || rawRole.contains("Founder", ignoreCase = true) -> "Creators"
            else -> "Builders"
        }
        val skills = obj.arr("skills")?.mapNotNull { it.asStr() }
            ?: obj.str("skills")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: listOf("Systems Architecture", "Prototyping")

        return BackendTalent(
            id = obj.str("id") ?: obj.str("_id") ?: "${System.nanoTime()}",
            name = obj.str("name") ?: "Specialist",
            role = title,
            roleCategory = roleCat,
            headline = obj.str("bio") ?: title,
            skills = skills,
            location = obj.str("location") ?: "Global",
            avatarUrl = obj.str("avatarUrl") ?: obj.str("avatar"),
            verified = obj.boolVal("verified") ?: true
        )
    }

    private fun parseProposal(
        obj: JsonObject,
        fallbackInnovationId: String = "",
        fallbackType: String = "Investment",
        fallbackAmount: String = "",
        fallbackMessage: String = "",
        fallbackContact: String = ""
    ): BackendProposal {
        val sender = obj.obj("sender") ?: obj.obj("user")
        val inn = obj.obj("innovation")
        return BackendProposal(
            id = obj.str("id") ?: obj.str("_id") ?: "${System.nanoTime()}",
            innovationId = obj.str("innovationId") ?: inn?.str("id") ?: fallbackInnovationId,
            innovationTitle = obj.str("innovationTitle") ?: inn?.str("title") ?: "Innovation Proposal",
            senderName = obj.str("senderName") ?: sender?.str("name") ?: "Partner",
            proposalType = obj.str("proposalType") ?: obj.str("type") ?: fallbackType,
            amount = obj.str("amount") ?: fallbackAmount,
            message = obj.str("message") ?: fallbackMessage,
            contactInfo = obj.str("contactInfo") ?: fallbackContact,
            status = obj.str("status") ?: "PENDING",
            createdAt = obj.str("createdAt") ?: "Recently"
        )
    }

    private fun buildSlidesFromSummary(innovation: BackendInnovation, rawText: String): List<PitchSlide> {
        val cleanLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (cleanLines.size >= 5) {
            val chunkSize = (cleanLines.size / 5).coerceAtLeast(1)
            val titles = listOf(
                "01. Vision & Opportunity",
                "02. Technical Breakthrough",
                "03. Market & Unit Economics",
                "04. IP Moat & Defensibility",
                "05. Capital Allocation & Roadmap"
            )
            return titles.mapIndexed { idx, title ->
                val start = idx * chunkSize
                val end = if (idx == 4) cleanLines.size else ((idx + 1) * chunkSize).coerceAtMost(cleanLines.size)
                val bullets = if (start < end) cleanLines.subList(start, end) else listOf(innovation.description)
                PitchSlide(
                    slideNumber = idx + 1,
                    title = title,
                    subtitle = innovation.category,
                    bullets = bullets
                )
            }
        }

        return listOf(
            PitchSlide(1, "01. Vision & Problem", innovation.title, listOf(innovation.description.take(180))),
            PitchSlide(2, "02. Product & Stage", "${innovation.stage} · ${innovation.category}", listOf("Led by ${innovation.authorName}", "Mutual NDA deal room ready for schematic inspection.")),
            PitchSlide(3, "03. Market Opportunity", "Commercial Scaling", listOf("High-growth demand in ${innovation.category}", "Positioned for licensing and strategic partnership distribution.")),
            PitchSlide(4, "04. IP & Defensibility", "USPTO-Aligned Protection", listOf("Confidential disclosures safeguarded under mutual digital NDA.", "Priority timestamped documentation.")),
            PitchSlide(5, "05. Capital Ask", "Funding Target: $${innovation.fundingGoal.toInt()}", listOf("Target Valuation: $${innovation.valuation.toInt()}", "Capital deployed toward manufacturing, certification, and GTM."))
        )
    }

    // --- JsonElement Safe Accessors ---

    private fun JsonElement.asObjOrEmpty(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
    private fun JsonElement.asStr(): String? = (this as? JsonPrimitive)?.takeIf { !it.isString || it.content != "null" }?.content
    private fun JsonObject.str(key: String): String? = this[key]?.asStr()?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.intVal(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
        ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()
    private fun JsonObject.doubleVal(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.boolVal(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    internal companion object {
        const val TAG = "BackendRepository"
        const val BASE_URL = "https://idea2impact-g8fe.onrender.com/api/"
        const val HEADER_AUTH_RETRY = "X-Auth-Retry"
        const val MAX_RECENT_SEARCHES = 10
        val RETRYABLE_HTTP_CODES = setOf(502, 503, 504)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val sharedJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private fun JsonElement.asObjOrEmpty(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
        private fun JsonElement.asStr(): String? = (this as? JsonPrimitive)?.takeIf { !it.isString || it.content != "null" }?.content
        private fun JsonObject.str(key: String): String? = this[key]?.asStr()?.takeIf { it.isNotBlank() && it != "null" }
        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.intVal(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
            ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()
        private fun JsonObject.boolVal(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

        internal fun validateCustomApiKey(customApiKey: String?): String? {
            val cleanKey = customApiKey?.trim()
            if (!cleanKey.isNullOrEmpty() && (cleanKey.length < 16 || cleanKey.any { it.isWhitespace() })) {
                throw BackendApiException(
                    code = 400,
                    message = "The saved custom API key format is invalid. Check Key Settings or switch back to Default AI."
                )
            }
            return cleanKey
        }

        internal fun parseAiChatResponse(rawJson: String, httpCode: Int = 200): String {
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI Sana backend returned an empty response.")
            }
            val root = try {
                sharedJson.parseToJsonElement(rawJson)
            } catch (e: SerializationException) {
                throw BackendApiException(
                    code = if (httpCode in 400..599) httpCode else 502,
                    message = "Custom AI backend returned a non-JSON response (HTTP $httpCode)."
                )
            }
            return extractAiChatReply(root)
        }

        internal fun extractAiChatReply(root: JsonElement): String {
            val obj = root.asObjOrEmpty()
            val dataObj = obj.obj("data")
            val explicitError = obj.str("error")
                ?: obj.obj("error")?.str("message")
                ?: dataObj?.str("error")
                ?: dataObj?.obj("error")?.str("message")
            val isExplicitFailure = obj.boolVal("success") == false ||
                obj.boolVal("ok") == false ||
                dataObj?.boolVal("success") == false

            if (!explicitError.isNullOrBlank() || isExplicitFailure) {
                val code = obj.intVal("statusCode")
                    ?: obj.intVal("code")
                    ?: obj.obj("error")?.intVal("code")
                    ?: 502
                val errMsg = explicitError
                    ?: obj.str("message")
                    ?: obj.str("detail")
                    ?: "Custom AI backend returned an error."
                throw BackendApiException(code, SanaPromptEngine.redactSensitiveInfo(errMsg))
            }

            val text = obj.str("reply")
                ?: obj.str("response")
                ?: obj.str("answer")
                ?: obj.str("text")
                ?: obj.str("content")
                ?: dataObj?.str("reply")
                ?: dataObj?.str("response")
                ?: dataObj?.str("text")
                ?: obj.str("message")
            return text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI Sana backend returned an empty or unrecognized response.")
        }
    }
}

class BackendApiException(val code: Int, message: String) : Exception(message)
