package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiFailureCategory
import com.example.data.model.AiRequestPhase
import com.example.data.model.BackendInnovation
import com.example.data.model.BackendTalent
import com.example.data.model.CopilotMessage
import com.example.data.model.Innovation
import com.example.data.model.InvestorOnePager
import com.example.data.model.PitchDeckResult
import com.example.data.model.PitchSlide
import com.example.util.AppCheckProbeOutcome
import com.example.util.DeviceConnectivityState
import com.example.util.FirebaseHttpsProbeResult
import com.example.util.NetworkDiagnostics
import com.example.util.lastAppCheckInitStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val TAG = "AiCopilotRepository"
private const val AI_TIMEOUT_MS = 60_000L
private const val AI_PRE_FALLBACK_ATTEMPT_TIMEOUT_MS = 24_000L
private const val AI_FALLBACK_STREAM_TIMEOUT_MS = 28_000L
private const val SDK_REQUEST_TIMEOUT_MS = 55_000L
private const val APP_CHECK_PROBE_TIMEOUT_MS = 3_500L
private const val PRE_CHUNK_RETRY_BACKOFF_MS = 800L
private const val MAX_PRE_CHUNK_ATTEMPTS = 2
internal const val DEEP_MODEL_NAME = "gemini-3.8-flash"
internal const val FAST_MODEL_NAME = "gemini-3.5-flash-lite"

class AiCopilotDiagnosticException(
    val route: String,
    val modelName: String,
    val phase: String,
    val elapsedMs: Long,
    val chunkCount: Int,
    val appCheckStatus: String,
    val partialContent: String = "",
    cause: Throwable? = null,
    val category: AiFailureCategory = AiFailureCategory.UNKNOWN,
    val fallbackAttempted: Boolean = false,
    val primaryFailureNote: String? = null
) : IllegalStateException(
    "AI Sana failed ($route / $modelName) during $phase after ${elapsedMs}ms " +
        "(chunks=$chunkCount, category=$category, appCheck=$appCheckStatus): " +
        SanaPromptEngine.redactSensitiveInfo(cause?.message ?: "unknown error"),
    cause
)

class AiCopilotRepository(private val appContext: Context? = null) {

    private val deepRequestOptions = RequestOptions(SDK_REQUEST_TIMEOUT_MS)
    private val fastRequestOptions = RequestOptions(SDK_REQUEST_TIMEOUT_MS)

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEEP_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.45f
                topP = 0.9f
                maxOutputTokens = 3072
                // Keep Deep mode responsive so extended reasoning does not stall TTFT past timeout.
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = deepRequestOptions,
            systemInstruction = content {
                text(SanaPromptEngine.buildSystemInstruction(isFastMode = false))
            }
        )
    }

    private val fastChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.55f
                topP = 0.9f
                maxOutputTokens = 1800
            },
            requestOptions = fastRequestOptions,
            systemInstruction = content {
                text(SanaPromptEngine.buildSystemInstruction(isFastMode = true))
            }
        )
    }

    private val onePagerSchema = Schema.obj(
        properties = mapOf(
            "executiveSummary" to Schema.string(description = "2-3 sentence investment thesis summary"),
            "problemStatement" to Schema.string(description = "Specific market pain point and urgency"),
            "solutionAdvantage" to Schema.string(description = "Technical differentiation and defensible IP moat"),
            "marketOpportunity" to Schema.string(description = "Bottom-up TAM/SAM/SOM estimate and target customer"),
            "suggestedTranche" to Schema.string(description = "Recommended pre-seed/seed raise and use of funds"),
            "milestones" to Schema.array(
                items = Schema.string(),
                description = "4 concrete 90-day validation or commercialization milestones"
            )
        )
    )

    private val structuredPrimaryRequestOptions = RequestOptions(30_000L)
    private val structuredFallbackRequestOptions = RequestOptions(45_000L)

    private val onePagerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEEP_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = onePagerSchema
                maxOutputTokens = 1200
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = structuredPrimaryRequestOptions
        )
    }

    private val fastOnePagerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = onePagerSchema
                maxOutputTokens = 1200
            },
            requestOptions = structuredFallbackRequestOptions
        )
    }

    private val pitchDeckSchema = Schema.obj(
        properties = mapOf(
            "slides" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "slideNumber" to Schema.integer(description = "1 to 5"),
                        "title" to Schema.string(description = "Crisp slide heading, e.g., 01. Vision & Opportunity"),
                        "subtitle" to Schema.string(description = "One-line takeaway for investors"),
                        "bullets" to Schema.array(
                            items = Schema.string(),
                            description = "3 concise institutional-grade bullet points"
                        )
                    )
                ),
                description = "Exactly 5 executive investor pitch deck slides"
            )
        )
    )

    private val pitchDeckModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEEP_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pitchDeckSchema
                maxOutputTokens = 1400
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = structuredPrimaryRequestOptions
        )
    }

    private val fastPitchDeckModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pitchDeckSchema
                maxOutputTokens = 1400
            },
            requestOptions = structuredFallbackRequestOptions
        )
    }

    private suspend fun probeAppCheckStatus(): AppCheckProbeOutcome {
        val connectivity = NetworkDiagnostics.checkDeviceConnectivity(appContext)
        val initStatus = lastAppCheckInitStatus
        if (connectivity == DeviceConnectivityState.OFFLINE) {
            Log.w(
                TAG,
                "AI Sana preflight aborted: device offline [build=${initStatus.buildType}, project=${initStatus.projectId}, provider=${initStatus.provider}]"
            )
            return AppCheckProbeOutcome(
                statusLabel = "device_offline",
                abortCategory = AiFailureCategory.NETWORK_OFFLINE,
                detail = "Device reports no active internet connection."
            )
        }

        return try {
            val tokenResult = withTimeoutOrNull(APP_CHECK_PROBE_TIMEOUT_MS) {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            }
            if (tokenResult != null) {
                if (tokenResult.token.isNotBlank()) {
                    AppCheckProbeOutcome(statusLabel = "token_ready")
                } else {
                    Log.w(TAG, "App Check returned empty token [build=${initStatus.buildType}, project=${initStatus.projectId}]")
                    AppCheckProbeOutcome(statusLabel = "empty_token")
                }
            } else {
                val httpsProbe = NetworkDiagnostics.probeFirebaseHttps()
                when (httpsProbe) {
                    is FirebaseHttpsProbeResult.DnsOrSocketUnreachable -> {
                        Log.w(TAG, "App Check timed out and Firebase HTTPS is unreachable: ${httpsProbe.reason}")
                        AppCheckProbeOutcome(
                            statusLabel = "network_unreachable",
                            abortCategory = AiFailureCategory.NETWORK_OFFLINE,
                            detail = httpsProbe.reason,
                            cause = httpsProbe.cause
                        )
                    }
                    is FirebaseHttpsProbeResult.TlsOrHttpsBlocked -> {
                        Log.w(TAG, "App Check timed out and Firebase HTTPS is blocked: ${httpsProbe.reason}")
                        AppCheckProbeOutcome(
                            statusLabel = "https_blocked",
                            abortCategory = AiFailureCategory.NETWORK_BLOCKED,
                            detail = httpsProbe.reason,
                            cause = httpsProbe.cause
                        )
                    }
                    is FirebaseHttpsProbeResult.Reachable -> {
                        Log.w(
                            TAG,
                            "App Check token fetch exceeded ${APP_CHECK_PROBE_TIMEOUT_MS}ms despite reachable HTTPS (HTTP ${httpsProbe.httpCode}); continuing to stream."
                        )
                        AppCheckProbeOutcome(statusLabel = "probe_timeout_${APP_CHECK_PROBE_TIMEOUT_MS}ms")
                    }
                }
            }
        } catch (err: Exception) {
            if (err is CancellationException) throw err
            val httpsProbe = NetworkDiagnostics.probeFirebaseHttps()
            val outcome = NetworkDiagnostics.classifyFirebaseProbeException(
                error = err,
                connectivity = connectivity,
                httpsProbe = httpsProbe
            )
            Log.e(
                TAG,
                "App Check probe error [build=${initStatus.buildType}, project=${initStatus.projectId}, " +
                    "provider=${initStatus.provider}, tokenSource=${initStatus.tokenSource}, " +
                    "connectivity=$connectivity, httpsProbe=${httpsProbe.javaClass.simpleName}, " +
                    "outcome=${outcome.statusLabel}, category=${outcome.abortCategory}, detail=${outcome.detail}]"
            )
            outcome
        }
    }

    fun streamBackendCopilotReply(
        prompt: String,
        history: List<CopilotMessage>,
        activeInnovation: BackendInnovation?,
        attachedImage: Bitmap? = null,
        useFastModel: Boolean = false,
        catalogContext: List<BackendInnovation> = emptyList(),
        talentContext: List<BackendTalent> = emptyList(),
        directorySummaryLines: List<String> = emptyList(),
        workbookSearchResult: WorkbookSearchResult? = null,
        onPhaseChange: (AiRequestPhase) -> Unit = {},
        onModelResolved: (route: String, modelName: String, switchedFromModel: String?, fallbackReason: AiFailureCategory?) -> Unit = { _, _, _, _ -> }
    ): Flow<String> {
        val recentHistory = SanaPromptEngine.filterUsableHistory(history, maxTurns = 16)
            .map { msg ->
                content(role = if (msg.isAssistant) "model" else "user") {
                    text(msg.content.removeSuffix("\n\n*(Generation stopped)*"))
                }
            }

        val bundle = SanaPromptEngine.analyzeAndBuildContext(
            prompt = prompt,
            activeInnovation = activeInnovation,
            catalogContext = catalogContext,
            talentContext = talentContext,
            directorySummaryLines = directorySummaryLines,
            hasAttachedImage = attachedImage != null,
            workbookSearchResult = workbookSearchResult
        )

        val currentPromptText = buildString {
            if (bundle.referenceContextBlock.isNotBlank()) {
                appendLine(bundle.referenceContextBlock)
                appendLine()
            }
            if (attachedImage != null) {
                appendLine("Inspect the attached prototype, schematic, or diagram carefully and integrate visual observations directly into your analysis.")
            }
            append(prompt.trim())
        }

        val currentContent = content(role = "user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(currentPromptText)
        }

        val requestContents = recentHistory + currentContent
        return executeStreamWithRecovery(
            useFastModel = useFastModel,
            historyTurnCount = recentHistory.size,
            hasAttachedImage = attachedImage != null,
            isGreeting = bundle.isGreeting,
            appCheckProbe = { "token_ready" },
            preflightCheck = { probeAppCheckStatus() },
            streamFactory = { isFast ->
                val targetModel = if (isFast) fastChatModel else chatModel
                flow {
                    targetModel.generateContentStream(requestContents).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            emit(piece)
                        }
                    }
                }
            },
            sleeper = { delay(it) },
            logger = { level, msg, err ->
                when (level) {
                    "W" -> Log.w(TAG, msg)
                    "E" -> Log.e(TAG, msg, err)
                    else -> Log.d(TAG, msg)
                }
            },
            onPhaseChange = onPhaseChange,
            onModelResolved = onModelResolved
        ).flowOn(Dispatchers.IO)
    }

    internal fun executeStreamWithRecovery(
        useFastModel: Boolean,
        historyTurnCount: Int = 0,
        hasAttachedImage: Boolean = false,
        isGreeting: Boolean = false,
        attemptTimeoutMs: Long = AI_PRE_FALLBACK_ATTEMPT_TIMEOUT_MS,
        fallbackTimeoutMs: Long = AI_FALLBACK_STREAM_TIMEOUT_MS,
        preChunkBackoffMs: Long = PRE_CHUNK_RETRY_BACKOFF_MS,
        appCheckProbe: suspend () -> String,
        preflightCheck: (suspend () -> AppCheckProbeOutcome)? = null,
        streamFactory: (useFast: Boolean) -> Flow<String>,
        sleeper: suspend (Long) -> Unit = { delay(it) },
        logger: (level: String, message: String, error: Throwable?) -> Unit = { _, _, _ -> },
        onPhaseChange: (AiRequestPhase) -> Unit = {},
        onModelResolved: (route: String, modelName: String, switchedFromModel: String?, fallbackReason: AiFailureCategory?) -> Unit = { _, _, _, _ -> }
    ): Flow<String> = flow {
        onPhaseChange(AiRequestPhase.CHECKING_CONNECTION)
        val preflight = preflightCheck?.invoke() ?: AppCheckProbeOutcome(statusLabel = appCheckProbe())
        val appCheckStatus = preflight.statusLabel

        val shouldAbortPreflight = preflight.abortCategory in setOf(
            AiFailureCategory.NETWORK_OFFLINE,
            AiFailureCategory.NETWORK_BLOCKED
        )
        if (shouldAbortPreflight) {
            val primaryRoute = if (useFastModel) "fast_flash_lite" else "deep_flash"
            val primaryModel = if (useFastModel) FAST_MODEL_NAME else DEEP_MODEL_NAME
            throw AiCopilotDiagnosticException(
                route = primaryRoute,
                modelName = primaryModel,
                phase = "network_preflight",
                elapsedMs = 0L,
                chunkCount = 0,
                appCheckStatus = appCheckStatus,
                cause = preflight.cause ?: IllegalStateException(preflight.detail.ifBlank { appCheckStatus }),
                category = preflight.abortCategory!!
            )
        } else if (preflight.abortCategory == AiFailureCategory.APP_CHECK_OR_AUTH) {
            logger(
                "W",
                "App Check preflight reported non-blocking issue ($appCheckStatus); proceeding to Firebase AI direct request.",
                preflight.cause
            )
        }

        suspend fun runModelWithPreChunkRetry(
            isFast: Boolean,
            switchedFromModel: String?,
            fallbackReason: AiFailureCategory?,
            timeoutForAttemptMs: Long,
            emitChunks: suspend (String) -> Unit
        ): Result<Unit> {
            val route = if (isFast) "fast_flash_lite" else "deep_flash"
            val modelName = if (isFast) FAST_MODEL_NAME else DEEP_MODEL_NAME
            val maxAttempts = MAX_PRE_CHUNK_ATTEMPTS

            for (attempt in 1..maxAttempts) {
                val accumulated = StringBuilder()
                val attemptStartMs = System.currentTimeMillis()
                var phase = "awaiting_first_chunk"
                var chunkCount = 0

                try {
                    logger(
                        "D",
                        "Starting AI Sana stream [route=$route, model=$modelName, attempt=$attempt/$maxAttempts, appCheck=$appCheckStatus, historyTurns=$historyTurnCount, hasImage=$hasAttachedImage, greeting=$isGreeting]",
                        null
                    )
                    onPhaseChange(if (switchedFromModel != null && attempt == 1) AiRequestPhase.SWITCHING_MODEL else AiRequestPhase.THINKING)

                    withTimeout(timeoutForAttemptMs) {
                        streamFactory(isFast).collect { piece ->
                            if (piece.isNotEmpty()) {
                                if (chunkCount == 0) {
                                    phase = "streaming_chunks"
                                    onModelResolved(route, modelName, switchedFromModel, fallbackReason)
                                    onPhaseChange(AiRequestPhase.STREAMING)
                                    logger(
                                        "D",
                                        "AI Sana first chunk received [route=$route, model=$modelName, ttftMs=${System.currentTimeMillis() - attemptStartMs}]",
                                        null
                                    )
                                }
                                chunkCount++
                                accumulated.append(piece)
                                emitChunks(accumulated.toString())
                            }
                        }
                    }

                    if (accumulated.isBlank()) {
                        val elapsed = System.currentTimeMillis() - attemptStartMs
                        return Result.failure(
                            AiCopilotDiagnosticException(
                                route = route,
                                modelName = modelName,
                                phase = "empty_completion",
                                elapsedMs = elapsed,
                                chunkCount = chunkCount,
                                appCheckStatus = appCheckStatus,
                                category = AiFailureCategory.EMPTY_RESPONSE
                            )
                        )
                    }
                    return Result.success(Unit)
                } catch (e: TimeoutCancellationException) {
                    val elapsed = System.currentTimeMillis() - attemptStartMs
                    val diag = AiCopilotDiagnosticException(
                        route = route,
                        modelName = modelName,
                        phase = phase,
                        elapsedMs = elapsed,
                        chunkCount = chunkCount,
                        appCheckStatus = appCheckStatus,
                        partialContent = accumulated.toString(),
                        cause = e,
                        category = AiFailureCategory.TIMEOUT
                    )
                    // NEVER retry automatically once partial answer text has started streaming.
                    if (chunkCount > 0) {
                        logger("E", diag.message.orEmpty(), diag)
                        return Result.failure(diag)
                    }
                    val canRetryTimeout = attempt < maxAttempts && elapsed < (timeoutForAttemptMs * 0.75)
                    if (canRetryTimeout) {
                        val delayMs = preChunkBackoffMs * attempt
                        onPhaseChange(AiRequestPhase.RETRYING)
                        logger("W", "Timeout before first chunk [route=$route, model=$modelName]; retrying in ${delayMs}ms", null)
                        sleeper(delayMs)
                        continue
                    }
                    logger("E", diag.message.orEmpty(), diag)
                    return Result.failure(diag)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - attemptStartMs
                    val rawCategory = SanaPromptEngine.classifyAiError(e)
                    val category = if (chunkCount > 0 && rawCategory in setOf(
                            AiFailureCategory.NETWORK_OFFLINE,
                            AiFailureCategory.UNKNOWN
                        )
                    ) {
                        AiFailureCategory.STREAMING_INTERRUPTION
                    } else {
                        rawCategory
                    }
                    val diag = (e as? AiCopilotDiagnosticException) ?: AiCopilotDiagnosticException(
                        route = route,
                        modelName = modelName,
                        phase = phase,
                        elapsedMs = elapsed,
                        chunkCount = chunkCount,
                        appCheckStatus = appCheckStatus,
                        partialContent = accumulated.toString(),
                        cause = e,
                        category = category
                    )
                    // NEVER restart automatically after partial answer text has appeared.
                    if (chunkCount > 0) {
                        logger("E", diag.message.orEmpty(), diag)
                        return Result.failure(diag)
                    }
                    val retryableBeforeFirstChunk = category in setOf(
                        AiFailureCategory.PROVIDER_OVERLOAD,
                        AiFailureCategory.QUOTA_OR_RATE_LIMIT
                    )
                    if (retryableBeforeFirstChunk && attempt < maxAttempts) {
                        val delayMs = preChunkBackoffMs * attempt
                        onPhaseChange(AiRequestPhase.RETRYING)
                        logger(
                            "W",
                            "Transient provider failure before first chunk [route=$route, model=$modelName, category=$category]; retry ${attempt + 1}/$maxAttempts in ${delayMs}ms",
                            null
                        )
                        sleeper(delayMs)
                        continue
                    }
                    logger("E", diag.message.orEmpty(), diag)
                    return Result.failure(diag)
                }
            }
            return Result.failure(
                AiCopilotDiagnosticException(
                    route = route,
                    modelName = modelName,
                    phase = "awaiting_first_chunk",
                    elapsedMs = 0L,
                    chunkCount = 0,
                    appCheckStatus = appCheckStatus,
                    category = AiFailureCategory.UNKNOWN
                )
            )
        }

        val primaryResult = runModelWithPreChunkRetry(
            isFast = useFastModel,
            switchedFromModel = null,
            fallbackReason = null,
            timeoutForAttemptMs = if (!useFastModel) attemptTimeoutMs else AI_TIMEOUT_MS,
            emitChunks = { emit(it) }
        )

        if (primaryResult.isSuccess) return@flow

        val primaryError = primaryResult.exceptionOrNull() as? AiCopilotDiagnosticException
            ?: throw (primaryResult.exceptionOrNull() ?: IllegalStateException("AI Sana stream failed"))

        val canFallbackToFast = !useFastModel &&
            primaryError.chunkCount == 0 &&
            primaryError.category in setOf(
                AiFailureCategory.PROVIDER_OVERLOAD,
                AiFailureCategory.TIMEOUT,
                AiFailureCategory.QUOTA_OR_RATE_LIMIT,
                AiFailureCategory.MODEL_UNAVAILABLE,
                AiFailureCategory.EMPTY_RESPONSE
            )

        if (!canFallbackToFast) {
            throw primaryError
        }

        logger(
            "W",
            "Deep mode ($DEEP_MODEL_NAME) still unavailable [category=${primaryError.category}, phase=${primaryError.phase}]; switching to Fast mode ($FAST_MODEL_NAME)",
            null
        )
        onPhaseChange(AiRequestPhase.SWITCHING_MODEL)

        val fallbackResult = runModelWithPreChunkRetry(
            isFast = true,
            switchedFromModel = DEEP_MODEL_NAME,
            fallbackReason = primaryError.category,
            timeoutForAttemptMs = fallbackTimeoutMs,
            emitChunks = { emit(it) }
        )

        if (fallbackResult.isSuccess) return@flow

        val fallbackErr = fallbackResult.exceptionOrNull()
        val diagFallback = fallbackErr as? AiCopilotDiagnosticException
        val primaryNote = "Deep mode ($DEEP_MODEL_NAME) failed (${primaryError.category}, phase=${primaryError.phase})"
        if (diagFallback != null) {
            throw AiCopilotDiagnosticException(
                route = diagFallback.route,
                modelName = diagFallback.modelName,
                phase = diagFallback.phase,
                elapsedMs = diagFallback.elapsedMs,
                chunkCount = diagFallback.chunkCount,
                appCheckStatus = diagFallback.appCheckStatus,
                partialContent = diagFallback.partialContent,
                cause = diagFallback.cause ?: diagFallback,
                category = diagFallback.category,
                fallbackAttempted = true,
                primaryFailureNote = primaryNote
            )
        }
        throw (fallbackErr ?: primaryError)
    }

    suspend fun generateBackendPitchDeck(innovation: BackendInnovation): PitchDeckResult {
        val prompt = """
            Generate a crisp 5-slide institutional investor pitch deck for this innovation:
            Title: ${innovation.title}
            Category: ${innovation.category}
            Stage: ${innovation.stage}
            Creator: ${innovation.authorName} (${innovation.authorTitle})
            Funding Target: $${innovation.fundingGoal.toInt()}
            Target Valuation: $${innovation.valuation.toInt()}
            NDA Required: ${innovation.ndaRequired}
            Description: ${innovation.description}
            
            Slide 1: Vision & Problem
            Slide 2: Technical Breakthrough & Architecture
            Slide 3: Market Opportunity & Unit Economics
            Slide 4: IP Moat & Defensibility
            Slide 5: Capital Allocation & 90-Day Roadmap
        """.trimIndent()

        return try {
        val preflight = probeAppCheckStatus()
        val appCheckStatus = preflight.statusLabel
        if (preflight.abortCategory in setOf(AiFailureCategory.NETWORK_OFFLINE, AiFailureCategory.NETWORK_BLOCKED)) {
            throw AiCopilotDiagnosticException(
                route = "deep_flash",
                modelName = DEEP_MODEL_NAME,
                phase = "network_preflight",
                elapsedMs = 0L,
                chunkCount = 0,
                appCheckStatus = appCheckStatus,
                cause = preflight.cause ?: IllegalStateException(preflight.detail.ifBlank { appCheckStatus }),
                category = preflight.abortCategory!!
            )
        } else if (preflight.abortCategory == AiFailureCategory.APP_CHECK_OR_AUTH) {
            Log.w(TAG, "App Check preflight non-blocking for pitch deck ($appCheckStatus); attempting Firebase AI directly.")
        }
            Log.d(TAG, "Generating pitch deck [primary=$DEEP_MODEL_NAME, fallback=$FAST_MODEL_NAME, appCheck=$appCheckStatus]")
            val response = try {
                withTimeout(22_000L) {
                    pitchDeckModel.generateContent(prompt)
                }
            } catch (primaryErr: CancellationException) {
                if (primaryErr !is TimeoutCancellationException) throw primaryErr
                Log.w(TAG, "Primary pitch deck model timed out; falling back to $FAST_MODEL_NAME", primaryErr)
                withTimeout(45_000L) {
                    fastPitchDeckModel.generateContent(prompt)
                }
            } catch (primaryErr: Exception) {
                val category = SanaPromptEngine.classifyAiError(primaryErr)
                val msgLower = primaryErr.message.orEmpty().lowercase()
                val shouldFallback = category in setOf(
                    AiFailureCategory.TIMEOUT,
                    AiFailureCategory.PROVIDER_OVERLOAD,
                    AiFailureCategory.QUOTA_OR_RATE_LIMIT,
                    AiFailureCategory.MODEL_UNAVAILABLE
                ) || msgLower.contains("allotted time") || msgLower.contains("timeout") || msgLower.contains("deadline")
                if (!shouldFallback) throw primaryErr
                Log.w(TAG, "Primary pitch deck model failed ($category); falling back to $FAST_MODEL_NAME", primaryErr)
                withTimeout(45_000L) {
                    fastPitchDeckModel.generateContent(prompt)
                }
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty structured response from AI Sana.")
            val root = JSONObject(jsonText)
            val slidesArray = root.getJSONArray("slides")
            val slides = (0 until slidesArray.length()).map { idx ->
                val obj = slidesArray.getJSONObject(idx)
                val bulletsArr = obj.optJSONArray("bullets")
                val bullets = if (bulletsArr != null) {
                    (0 until bulletsArr.length()).map { bulletsArr.getString(it) }
                } else emptyList()
                PitchSlide(
                    slideNumber = obj.optInt("slideNumber", idx + 1),
                    title = obj.getString("title"),
                    subtitle = obj.optString("subtitle", ""),
                    bullets = bullets
                )
            }.take(5)

            if (slides.isEmpty()) {
                throw IllegalStateException("AI Sana returned 0 pitch deck slides.")
            }
            PitchDeckResult(innovationTitle = innovation.title, slides = slides)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Pitch deck generation timed out on both $DEEP_MODEL_NAME and $FAST_MODEL_NAME", e)
            throw IllegalStateException("Pitch deck generation timed out. Please check your connection and tap Retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate structured pitch deck", e)
            throw e
        }
    }

    fun streamCopilotReply(prompt: String, activeInnovation: Innovation?): Flow<String> = flow {
        val fullPrompt = buildString {
            if (activeInnovation != null) {
                appendLine("Active Innovation Context:")
                appendLine("Title: ${activeInnovation.title}")
                appendLine("Category: ${activeInnovation.category}")
                appendLine("Valuation / Ask: $${activeInnovation.price.toInt()}")
                appendLine("Creator: ${activeInnovation.authorName}")
                appendLine("Description: ${activeInnovation.description}")
                appendLine()
            }
            append("User Request: ")
            append(prompt)
        }

        val accumulated = StringBuilder()
        val startMs = System.currentTimeMillis()
        var chunkCount = 0
        var phase = "awaiting_first_chunk"
        val preflight = probeAppCheckStatus()
        val appCheckStatus = preflight.statusLabel
        if (preflight.abortCategory in setOf(AiFailureCategory.NETWORK_OFFLINE, AiFailureCategory.NETWORK_BLOCKED)) {
            throw AiCopilotDiagnosticException(
                route = "deep_flash",
                modelName = DEEP_MODEL_NAME,
                phase = "network_preflight",
                elapsedMs = 0L,
                chunkCount = 0,
                appCheckStatus = appCheckStatus,
                cause = preflight.cause ?: IllegalStateException(preflight.detail.ifBlank { appCheckStatus }),
                category = preflight.abortCategory!!
            )
        } else if (preflight.abortCategory == AiFailureCategory.APP_CHECK_OR_AUTH) {
            Log.w(TAG, "App Check preflight non-blocking for legacy stream ($appCheckStatus); attempting Firebase AI directly.")
        }
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chatModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        phase = "streaming_chunks"
                        chunkCount++
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw AiCopilotDiagnosticException(
                    route = "deep_flash",
                    modelName = DEEP_MODEL_NAME,
                    phase = "empty_completion",
                    elapsedMs = System.currentTimeMillis() - startMs,
                    chunkCount = chunkCount,
                    appCheckStatus = appCheckStatus
                )
            }
        } catch (e: TimeoutCancellationException) {
            throw AiCopilotDiagnosticException(
                route = "deep_flash",
                modelName = DEEP_MODEL_NAME,
                phase = phase,
                elapsedMs = System.currentTimeMillis() - startMs,
                chunkCount = chunkCount,
                appCheckStatus = appCheckStatus,
                partialContent = accumulated.toString(),
                cause = e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiCopilotDiagnosticException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Sana streaming failed", e)
            throw AiCopilotDiagnosticException(
                route = "deep_flash",
                modelName = DEEP_MODEL_NAME,
                phase = phase,
                elapsedMs = System.currentTimeMillis() - startMs,
                chunkCount = chunkCount,
                appCheckStatus = appCheckStatus,
                partialContent = accumulated.toString(),
                cause = e
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateInvestorOnePager(innovation: Innovation): InvestorOnePager {
        val prompt = """
            Generate a crisp institutional-grade Investor One-Pager memo for this innovation:
            Title: ${innovation.title}
            Category: ${innovation.category}
            Creator: ${innovation.authorName} (${innovation.authorHeadline ?: "Innovator"})
            Licensing Open: ${innovation.licensing}
            Funding Open: ${innovation.funding}
            Asking Price / Seed Target: $${innovation.price.toInt()}
            Description: ${innovation.description}
        """.trimIndent()

        return try {
            val response = try {
                withTimeout(22_000L) {
                    onePagerModel.generateContent(prompt)
                }
            } catch (primaryErr: CancellationException) {
                if (primaryErr !is TimeoutCancellationException) throw primaryErr
                Log.w(TAG, "Primary one-pager model timed out; falling back to $FAST_MODEL_NAME", primaryErr)
                withTimeout(45_000L) {
                    fastOnePagerModel.generateContent(prompt)
                }
            } catch (primaryErr: Exception) {
                val msgLower = primaryErr.message.orEmpty().lowercase()
                val category = SanaPromptEngine.classifyAiError(primaryErr)
                if (category !in setOf(AiFailureCategory.TIMEOUT, AiFailureCategory.PROVIDER_OVERLOAD, AiFailureCategory.QUOTA_OR_RATE_LIMIT) &&
                    !msgLower.contains("allotted time") && !msgLower.contains("timeout")
                ) {
                    throw primaryErr
                }
                Log.w(TAG, "Primary one-pager model failed ($category); falling back to $FAST_MODEL_NAME", primaryErr)
                withTimeout(45_000L) {
                    fastOnePagerModel.generateContent(prompt)
                }
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty structured response from AI Sana.")
            val json = JSONObject(jsonText)
            val milestonesArr = json.optJSONArray("milestones")
            val milestones = if (milestonesArr != null) {
                (0 until milestonesArr.length()).map { milestonesArr.getString(it) }
            } else emptyList()

            InvestorOnePager(
                executiveSummary = json.getString("executiveSummary"),
                problemStatement = json.getString("problemStatement"),
                solutionAdvantage = json.getString("solutionAdvantage"),
                marketOpportunity = json.getString("marketOpportunity"),
                suggestedTranche = json.getString("suggestedTranche"),
                milestones = milestones
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Investor One-Pager timed out", e)
            throw IllegalStateException("One-Pager generation timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Investor One-Pager", e)
            throw e
        }
    }
}

