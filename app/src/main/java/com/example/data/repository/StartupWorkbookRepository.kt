package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.BackendInnovation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import kotlin.math.ln

private const val TAG = "StartupWorkbookRepo"
private const val WORKBOOK_ASSET_FILE = "india_startup_ideas_workbook.json"

@Serializable
data class WorkbookDataset(
    val datasetTitle: String,
    val workbook1Title: String,
    val workbook1Date: String,
    val workbook2Title: String,
    val workbook2Context: String,
    val coverageLimits: String,
    val totalEntries: Int,
    val entries: List<WorkbookEntry>
)

@Serializable
data class WorkbookEntry(
    val id: Int,
    val workbook: String,
    val title: String,
    val sector: String,
    val targetCustomer: String,
    val problem: String? = null,
    val offerOrConcept: String,
    val revenueModel: String? = null,
    val firstValidationTest: String,
    val buildEffort: String? = null,
    val indicativeBudget: String? = null,
    val keyRisks: String? = null,
    val contextSource: String? = null
)

enum class WorkbookAccessStatus {
    AVAILABLE,
    UNAVAILABLE
}

data class WorkbookSearchMatch(
    val entry: WorkbookEntry,
    val score: Double,
    val matchedTerms: List<String>
)

data class WorkbookSearchResult(
    val status: WorkbookAccessStatus,
    val unavailabilityReason: String? = null,
    val datasetTitle: String = "India Startup Ideas Reference Workbooks (1,600 Concepts)",
    val workbook1Date: String = "19 November 2026",
    val coverageLimits: String = "Contains 1,600 India-focused startup idea concepts (600 detailed/cluster briefs + 1,000 targeted sector variations). Concept prompts only — not a registry of existing companies.",
    val totalEntriesSearched: Int = 0,
    val querySummary: String = "",
    val matches: List<WorkbookSearchMatch> = emptyList(),
    val hasCloseMatches: Boolean = false
) {
    fun toPromptContextBlock(): String = buildString {
        appendLine("<workbook_reference_dataset>")
        appendLine("Dataset: $datasetTitle")
        appendLine("Workbook 1 Date: $workbook1Date (Ideas #1–600) | Workbook 2: Additional 1,000 India Startup Ideas (Ideas #601–1600, informed by Startup India 2026 categories, MSME Annual Report 2024–25, Economic Survey 2024–25)")
        appendLine("Coverage Limits: $coverageLimits")
        if (status == WorkbookAccessStatus.UNAVAILABLE) {
            appendLine("Access Status: UNAVAILABLE (${unavailabilityReason ?: "Workbook asset could not be loaded"})")
            appendLine("Instruction: Explicitly inform the user that you could not access or search the reference workbook right now. Do NOT pretend you searched it or fabricate any workbook entries.")
            append("</workbook_reference_dataset>")
            return@buildString
        }

        appendLine("Access Status: SEARCHED ($totalEntriesSearched total concept entries indexed and searched)")
        if (querySummary.isNotBlank()) {
            appendLine("Search Query Used: $querySummary")
        }
        appendLine("Match Assessment: ${if (hasCloseMatches) "SIMILAR_EXAMPLES_FOUND" else "NO_CLOSE_MATCH_FOUND"}")
        appendLine("Field Discipline: Cite ONLY the fields explicitly listed below for each entry. If a field such as revenue model, budget, website, state, or company name is not listed, do NOT invent or assume it.")

        if (matches.isEmpty()) {
            appendLine("Matches Found: None (0 close entries matched across all $totalEntriesSearched workbook concepts).")
        } else {
            val header = if (hasCloseMatches) {
                "Closest Workbook Entries (ranked by multi-field overlap):"
            } else {
                "No close match met the similarity threshold; below are the nearest loosely adjacent entries for reference only (state clearly that you did not find a close match in this dataset):"
            }
            appendLine(header)
            matches.forEachIndexed { idx, match ->
                val e = match.entry
                appendLine()
                appendLine("${idx + 1}. Idea #${e.id}: ${e.title}")
                appendLine("   - Workbook: ${e.workbook}")
                appendLine("   - Sector: ${e.sector}")
                val customerLabel = if (e.id <= 600) "Who pays / target customer" else "Who it serves"
                appendLine("   - $customerLabel: ${e.targetCustomer}")
                e.problem?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Problem to solve: $it")
                }
                val conceptLabel = if (e.id <= 600) "Offer / first product" else "Concept"
                appendLine("   - $conceptLabel: ${e.offerOrConcept}")
                e.revenueModel?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Possible revenue model: $it")
                }
                val testLabel = if (e.id <= 600) "First validation test (2–4 weeks)" else "First test"
                appendLine("   - $testLabel: ${e.firstValidationTest}")
                e.buildEffort?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Build effort: $it")
                }
                e.indicativeBudget?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Indicative initial budget: $it")
                }
                e.keyRisks?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Key risks / first checks: $it")
                }
                e.contextSource?.takeIf { it.isNotBlank() }?.let {
                    appendLine("   - Context source tag: $it")
                }
                if (match.matchedTerms.isNotEmpty()) {
                    appendLine("   - Overlapping keywords: ${match.matchedTerms.take(6).joinToString(", ")}")
                }
            }
        }
        append("</workbook_reference_dataset>")
    }
}

class StartupWorkbookRepository(
    private val appContext: Context? = null,
    private val rawJsonLoader: (() -> String)? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class IndexedEntry(
        val entry: WorkbookEntry,
        val titleTokens: Set<String>,
        val sectorTokens: Set<String>,
        val customerTokens: Set<String>,
        val problemTokens: Set<String>,
        val offerTokens: Set<String>,
        val revenueTokens: Set<String>,
        val allTokens: Set<String>,
        val clusterKey: String
    )

    private data class LoadedIndex(
        val dataset: WorkbookDataset,
        val entries: List<IndexedEntry>,
        val idf: Map<String, Double>
    )

    @Volatile
    private var cachedIndex: LoadedIndex? = null

    @Volatile
    private var loadErrorReason: String? = null

    private val lock = Any()

    fun isWorkbookAvailable(): Boolean = getOrLoadIndex() != null

    fun totalEntriesCount(): Int = getOrLoadIndex()?.dataset?.totalEntries ?: 0

    suspend fun searchAsync(
        prompt: String,
        activeInnovation: BackendInnovation? = null,
        limit: Int = 5
    ): WorkbookSearchResult = withContext(Dispatchers.IO) {
        search(prompt = prompt, activeInnovation = activeInnovation, limit = limit)
    }

    fun search(
        prompt: String,
        activeInnovation: BackendInnovation? = null,
        limit: Int = 5
    ): WorkbookSearchResult {
        val loaded = getOrLoadIndex()
        if (loaded == null) {
            return WorkbookSearchResult(
                status = WorkbookAccessStatus.UNAVAILABLE,
                unavailabilityReason = loadErrorReason ?: "Reference workbook asset is unavailable.",
                querySummary = prompt.trim().take(160)
            )
        }

        val combinedQuery = buildString {
            append(prompt.trim())
            if (activeInnovation != null && !prompt.lowercase().contains("my idea")) {
                append(' ')
                append(activeInnovation.title)
                append(' ')
                append(activeInnovation.category)
                append(' ')
                append(activeInnovation.description.take(240))
            }
        }.trim()

        val rawQueryTokens = tokenize(combinedQuery)
        if (rawQueryTokens.isEmpty()) {
            return WorkbookSearchResult(
                status = WorkbookAccessStatus.AVAILABLE,
                datasetTitle = loaded.dataset.datasetTitle,
                workbook1Date = loaded.dataset.workbook1Date,
                coverageLimits = loaded.dataset.coverageLimits,
                totalEntriesSearched = loaded.dataset.totalEntries,
                querySummary = combinedQuery.take(180),
                matches = emptyList(),
                hasCloseMatches = false
            )
        }

        // Build query term -> weight map (exact tokens = 1.0, domain synonyms = 0.55)
        val weightedTerms = LinkedHashMap<String, Double>()
        rawQueryTokens.forEach { tok ->
            weightedTerms[tok] = 1.0
            SYNONYM_MAP[tok]?.forEach { syn ->
                if (syn !in weightedTerms) {
                    weightedTerms[syn] = 0.55
                }
            }
        }

        val scored = ArrayList<Triple<IndexedEntry, Double, List<String>>>(64)
        for (item in loaded.entries) {
            var score = 0.0
            var exactTermHits = 0
            val matchedTerms = ArrayList<String>(8)

            for ((term, qWeight) in weightedTerms) {
                if (term !in item.allTokens) continue
                val idf = loaded.idf[term] ?: 1.0
                var fieldScore = 0.0
                if (term in item.titleTokens) fieldScore += 3.4
                if (term in item.sectorTokens) fieldScore += 2.3
                if (term in item.customerTokens) fieldScore += 1.9
                if (term in item.problemTokens) fieldScore += 1.9
                if (term in item.offerTokens) fieldScore += 1.5
                if (term in item.revenueTokens) fieldScore += 1.1

                score += fieldScore * qWeight * idf
                if (qWeight >= 0.99) {
                    exactTermHits++
                }
                if (matchedTerms.size < 8) {
                    matchedTerms.add(term)
                }
            }

            if (score <= 0.0) continue

            // Mild bonus for multi-concept overlap (rewards matching both problem/solution and customer/sector)
            val distinctHits = matchedTerms.size
            if (distinctHits >= 2) {
                score *= (1.0 + 0.12 * (distinctHits - 1).coerceAtMost(5))
            }
            if (exactTermHits >= 2) {
                score += 1.5
            }
            // Prefer Workbook 1 slightly on ties because it includes problem, revenue model, budget, and risk fields
            if (item.entry.id <= 600) {
                score += 0.25
            }

            scored.add(Triple(item, score, matchedTerms))
        }

        scored.sortByDescending { it.second }

        // Select top diverse matches (max 1 per cluster archetype unless very few clusters match)
        val selected = mutableListOf<WorkbookSearchMatch>()
        val usedClusters = HashMap<String, Int>()
        for ((indexed, score, matched) in scored) {
            if (selected.size >= limit) break
            val clusterCount = usedClusters.getOrDefault(indexed.clusterKey, 0)
            if (clusterCount >= 1) continue
            usedClusters[indexed.clusterKey] = clusterCount + 1
            selected.add(
                WorkbookSearchMatch(
                    entry = indexed.entry,
                    score = score,
                    matchedTerms = matched
                )
            )
        }
        // If diversity filter left us with fewer than 3 strong hits, allow a 2nd entry from top clusters
        if (selected.size < limit.coerceAtMost(3)) {
            val selectedIds = selected.map { it.entry.id }.toHashSet()
            for ((indexed, score, matched) in scored) {
                if (selected.size >= limit) break
                if (indexed.entry.id in selectedIds) continue
                if (score < 7.0) break
                selected.add(
                    WorkbookSearchMatch(
                        entry = indexed.entry,
                        score = score,
                        matchedTerms = matched
                    )
                )
                selectedIds.add(indexed.entry.id)
            }
        }

        val topMatch = selected.firstOrNull()
        val hasCloseMatches = topMatch != null && (
            (topMatch.score >= 8.5 && topMatch.matchedTerms.size >= 2) ||
                (rawQueryTokens.size <= 2 && topMatch.score >= 6.5 && topMatch.matchedTerms.isNotEmpty())
            )

        val finalMatches = if (hasCloseMatches) {
            selected.filter { it.score >= (topMatch!!.score * 0.42).coerceAtLeast(5.5) }.take(limit)
        } else {
            // Keep at most 2 weak/adjacent entries only if there is at least mild topical overlap
            selected.filter { it.score >= 4.5 }.take(2)
        }

        return WorkbookSearchResult(
            status = WorkbookAccessStatus.AVAILABLE,
            datasetTitle = loaded.dataset.datasetTitle,
            workbook1Date = loaded.dataset.workbook1Date,
            coverageLimits = loaded.dataset.coverageLimits,
            totalEntriesSearched = loaded.dataset.totalEntries,
            querySummary = combinedQuery.take(180),
            matches = finalMatches,
            hasCloseMatches = hasCloseMatches
        )
    }

    private fun getOrLoadIndex(): LoadedIndex? {
        cachedIndex?.let { return it }
        synchronized(lock) {
            cachedIndex?.let { return it }
            return try {
                val rawJson = when {
                    rawJsonLoader != null -> rawJsonLoader.invoke()
                    appContext != null -> appContext.assets.open(WORKBOOK_ASSET_FILE).use { input ->
                        InputStreamReader(input, Charsets.UTF_8).readText()
                    }
                    else -> {
                        loadErrorReason = "Android context and workbook loader are not initialized."
                        return null
                    }
                }
                val dataset = json.decodeFromString(WorkbookDataset.serializer(), rawJson)
                if (dataset.entries.isEmpty()) {
                    loadErrorReason = "Workbook dataset loaded with 0 entries."
                    return null
                }
                val built = buildIndex(dataset)
                cachedIndex = built
                loadErrorReason = null
                built
            } catch (e: Exception) {
                loadErrorReason = "Failed to load $WORKBOOK_ASSET_FILE: ${e.message ?: e.javaClass.simpleName}"
                runCatching { Log.e(TAG, loadErrorReason!!, e) }
                null
            }
        }
    }

    private fun buildIndex(dataset: WorkbookDataset): LoadedIndex {
        val docFreq = HashMap<String, Int>(2048)
        val indexedEntries = dataset.entries.map { entry ->
            val titleToks = tokenize(entry.title)
            val sectorToks = tokenize(entry.sector)
            val customerToks = tokenize(entry.targetCustomer)
            val problemToks = tokenize(entry.problem.orEmpty())
            val offerToks = tokenize(entry.offerOrConcept)
            val revenueToks = tokenize(entry.revenueModel.orEmpty())
            val all = HashSet<String>(titleToks.size + sectorToks.size + customerToks.size + problemToks.size + offerToks.size + revenueToks.size)
            all.addAll(titleToks)
            all.addAll(sectorToks)
            all.addAll(customerToks)
            all.addAll(problemToks)
            all.addAll(offerToks)
            all.addAll(revenueToks)
            for (t in all) {
                docFreq[t] = (docFreq[t] ?: 0) + 1
            }
            IndexedEntry(
                entry = entry,
                titleTokens = titleToks,
                sectorTokens = sectorToks,
                customerTokens = customerToks,
                problemTokens = problemToks,
                offerTokens = offerToks,
                revenueTokens = revenueToks,
                allTokens = all,
                clusterKey = computeClusterKey(entry)
            )
        }

        val totalDocs = indexedEntries.size.toDouble().coerceAtLeast(1.0)
        val idf = HashMap<String, Double>(docFreq.size)
        for ((term, df) in docFreq) {
            // Smooth IDF clamped to [0.35, 3.2] so ubiquitous terms ("small", "service") carry low weight
            val raw = ln((totalDocs + 1.0) / (df.toDouble() + 1.0)) + 0.5
            idf[term] = raw.coerceIn(0.35, 3.2)
        }
        return LoadedIndex(dataset = dataset, entries = indexedEntries, idf = idf)
    }

    private fun computeClusterKey(entry: WorkbookEntry): String {
        val lower = entry.title.lowercase()
        return when {
            entry.id in 101..600 -> {
                // Ideas 101-600 have 10 business-model variations per domain cluster:
                // e.g. "Discovery marketplace for <topic> — <geo>"
                val afterFor = lower.substringAfter(" for ", lower)
                afterFor.substringBefore(" — ").trim()
            }
            entry.id > 600 -> {
                // Ideas 601-1600 have 5 customer-segment variations per archetype:
                // e.g. "<archetype> for <customer segment>"
                "${entry.sector.lowercase()}::${lower.substringBefore(" for ").trim()}"
            }
            else -> "id:${entry.id}"
        }
    }

    companion object {
        private val TOKEN_SPLIT_REGEX = Regex("[^a-z0-9]+")

        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "for", "of", "to", "in", "on", "at", "by", "with", "from",
            "as", "is", "are", "was", "were", "be", "been", "being", "it", "its", "this", "that",
            "these", "those", "my", "our", "your", "their", "we", "i", "you", "they", "me", "us",
            "can", "could", "would", "should", "will", "want", "build", "building", "make", "making",
            "create", "creating", "start", "starting", "launch", "launching", "help", "helps", "using",
            "use", "used", "about", "into", "over", "under", "across", "around", "through", "between",
            "after", "before", "one", "two", "first", "new", "more", "most", "very", "also", "just",
            "only", "not", "no", "yes", "if", "when", "where", "how", "what", "which", "who", "why",
            "idea", "ideas", "startup", "startups", "business", "company", "app", "platform", "product",
            "solution", "evaluate", "evaluation", "assess", "review", "feedback", "compare", "workbook",
            "dataset", "india", "indian", "customers", "customer", "users", "user", "people", "market",
            "problem", "solve", "solving", "offer", "concept", "service", "services", "small", "local",
            "focus", "based", "like", "need", "needs", "give", "check", "please", "tell"
        )

        private val SYNONYM_GROUPS: List<Set<String>> = listOf(
            setOf("kirana", "retail", "shop", "store", "grocery", "merchant", "wholesal", "dukandar"),
            setOf("bill", "invoic", "gst", "bookkeep", "account", "ledger", "payment", "credit", "udhaar", "khata"),
            setOf("whatsapp", "chat", "messag", "voic"),
            setOf("clinic", "hospital", "doctor", "health", "healthcare", "diagnos", "patholog", "lab", "patient", "telemedicin", "pharmac"),
            setOf("farmer", "farm", "agri", "agricultur", "crop", "smallhold", "fpo", "mandi", "soil", "irrigat", "harvest", "dairy", "livestock"),
            setOf("solar", "rooftop", "pv", "renew", "clean", "energi", "power", "grid"),
            setOf("ev", "electric", "batteri", "charg", "scooter", "rickshaw", "fleet", "mobiliti"),
            setOf("water", "leak", "borewel", "tanker", "plumb", "sewag", "stewardship", "groundwat", "purif"),
            setOf("wast", "recycl", "scrap", "ewast", "plastic", "compost", "circular", "trash", "garbag", "landfil"),
            setOf("school", "student", "colleg", "tutor", "edtech", "learn", "exam", "coaching", "teacher", "skill", "vocation"),
            setOf("truck", "freight", "logist", "deliveri", "courier", "warehous", "cold", "chain", "fleet", "rout", "dispatch"),
            setOf("apart", "societi", "rwa", "housing", "build", "faciliti", "tenant", "rental", "properti"),
            setOf("textil", "apparel", "garment", "tailor", "fabric", "cloth", "fashion", "loom", "weaver"),
            setOf("food", "kitchen", "restaurant", "tiffin", "cater", "spic", "bakeri", "fmcg", "packag"),
            setOf("msme", "sme", "factori", "manufactur", "workshop", "machin", "vendor", "supplier"),
            setOf("elder", "senior", "caregiv", "homecar", "physio", "rehab", "accessib", "disab"),
            setOf("flood", "weather", "climat", "heat", "resilienc", "disast", "pollut", "air"),
            setOf("cyber", "secur", "fraud", "scam", "privaci", "complianc", "audit", "kyc"),
            setOf("repair", "mainten", "amc", "technician", "applianc", "electron", "servicing", "spare"),
            setOf("credit", "loan", "lending", "fintech", "insur", "microinsur", "upi", "emi", "working")
        )

        private val SYNONYM_MAP: Map<String, Set<String>> = buildMap {
            for (group in SYNONYM_GROUPS) {
                for (word in group) {
                    val existing = this[word]
                    this[word] = if (existing == null) group - word else (existing + group) - word
                }
            }
        }

        internal fun tokenize(text: String): Set<String> {
            if (text.isBlank()) return emptySet()
            val out = LinkedHashSet<String>()
            val parts = TOKEN_SPLIT_REGEX.split(text.lowercase())
            for (raw in parts) {
                if (raw.length < 2) continue
                if (raw in STOP_WORDS) continue
                val stemmed = stem(raw)
                if (stemmed.length >= 2 && stemmed !in STOP_WORDS) {
                    out.add(stemmed)
                }
            }
            return out
        }

        private fun stem(word: String): String {
            if (word.length <= 3) return word
            return when {
                word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "i"
                word.endsWith("ing") && word.length > 5 -> word.dropLast(3)
                word.endsWith("ers") && word.length > 5 -> word.dropLast(1)
                word.endsWith("ed") && word.length > 4 -> word.dropLast(2)
                word.endsWith("es") && word.length > 4 -> word.dropLast(2)
                word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> word.dropLast(1)
                else -> word
            }
        }
    }
}
