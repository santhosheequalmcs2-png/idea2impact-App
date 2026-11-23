package com.example.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class StartupWorkbookRepositoryTest {

    private fun resolveAssetFile(): File {
        val candidates = listOf(
            File("src/main/assets/india_startup_ideas_workbook.json"),
            File("app/src/main/assets/india_startup_ideas_workbook.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate india_startup_ideas_workbook.json from cwd=${File(".").absolutePath}")
    }

    private fun realRepository(): StartupWorkbookRepository {
        val assetFile = resolveAssetFile()
        return StartupWorkbookRepository(rawJsonLoader = { assetFile.readText(Charsets.UTF_8) })
    }

    @Test
    fun bundledWorkbook_loadsAll1600EntriesWithContiguousIdsAndMetadata() {
        val repo = realRepository()
        assertThat(repo.isWorkbookAvailable()).isTrue()
        assertThat(repo.totalEntriesCount()).isEqualTo(1600)

        val result = repo.search("WhatsApp billing and stock assistant for kirana retail shops")
        assertThat(result.status).isEqualTo(WorkbookAccessStatus.AVAILABLE)
        assertThat(result.totalEntriesSearched).isEqualTo(1600)
        assertThat(result.workbook1Date).isEqualTo("19 November 2026")
        assertThat(result.hasCloseMatches).isTrue()
        assertThat(result.matches).isNotEmpty()
        assertThat(result.matches.first().entry.id).isEqualTo(1)
        assertThat(result.matches.first().entry.title).contains("WhatsApp-first billing")
    }

    @Test
    fun search_matchesBothWorkbook1AndWorkbook2ConceptsAccurately() {
        val repo = realRepository()

        val solarResult = repo.search("I want to launch a rooftop solar panel maintenance subscription for homes and housing societies")
        assertThat(solarResult.hasCloseMatches).isTrue()
        assertThat(solarResult.matches.map { it.entry.id }).contains(10)

        val waterLedgerResult = repo.search("Building water-loss ledger and leak ticket tracking for apartment associations")
        assertThat(waterLedgerResult.hasCloseMatches).isTrue()
        assertThat(waterLedgerResult.matches.any { it.entry.id == 601 }).isTrue()

        val promptBlock = waterLedgerResult.toPromptContextBlock()
        assertThat(promptBlock).contains("<workbook_reference_dataset>")
        assertThat(promptBlock).contains("Idea #601")
        assertThat(promptBlock).contains("Who it serves: apartment associations")
        // Workbook 2 entries do not have revenueModel or indicativeBudget; verify we never fabricate them
        val idea601Section = promptBlock.substringAfter("Idea #601").substringBefore("Idea #")
        assertThat(idea601Section).doesNotContain("Possible revenue model:")
        assertThat(idea601Section).doesNotContain("Indicative initial budget:")
    }

    @Test
    fun search_unrelatedFuturisticIdea_reportsNoCloseMatch() {
        val repo = realRepository()
        val result = repo.search("Evaluate my idea: interstellar warp drive laser propulsion for deep-space asteroid terraforming")
        assertThat(result.status).isEqualTo(WorkbookAccessStatus.AVAILABLE)
        assertThat(result.hasCloseMatches).isFalse()

        val block = result.toPromptContextBlock()
        assertThat(block).contains("NO_CLOSE_MATCH_FOUND")
        assertThat(block).contains("19 November 2026")
    }

    @Test
    fun unavailableWorkbook_emitsExplicitHonestyInstruction() {
        val brokenRepo = StartupWorkbookRepository(rawJsonLoader = { throw IllegalStateException("Asset missing") })
        assertThat(brokenRepo.isWorkbookAvailable()).isFalse()

        val result = brokenRepo.search("WhatsApp billing app for shops")
        assertThat(result.status).isEqualTo(WorkbookAccessStatus.UNAVAILABLE)
        val block = result.toPromptContextBlock()
        assertThat(block).contains("Access Status: UNAVAILABLE")
        assertThat(block).contains("Do NOT pretend you searched it")
    }

    @Test
    fun sanaPromptEngine_injectsWorkbookContextForIdeaPitchesAndEnforces7StepGuidance() {
        val repo = realRepository()
        val prompt = "I want to build a WhatsApp billing and credit reminder tool for small kirana shops in tier-2 cities."
        assertThat(SanaPromptEngine.isIdeaSubmissionOrEvaluation(prompt)).isTrue()

        val searchResult = repo.search(prompt)
        val bundle = SanaPromptEngine.analyzeAndBuildContext(
            prompt = prompt,
            activeInnovation = null,
            workbookSearchResult = searchResult
        )
        assertThat(bundle.isIdeaEvaluation).isTrue()
        assertThat(bundle.referenceContextBlock).contains("<workbook_reference_dataset>")
        assertThat(bundle.referenceContextBlock).contains("Idea #1:")

        val sysPrompt = SanaPromptEngine.buildSystemInstruction(isFastMode = false)
        assertThat(sysPrompt).contains("I found similar examples in this dataset")
        assertThat(sysPrompt).contains("I did not find a close match in this dataset")
        assertThat(sysPrompt).contains("What would be different")
        assertThat(sysPrompt).contains("Which customer might value that difference")
        assertThat(sysPrompt).contains("What assumption needs testing")
        assertThat(sysPrompt).contains("A low-cost way to test it")
        assertThat(sysPrompt).contains("Next steps")
        assertThat(sysPrompt).contains("19 November 2026")
        assertThat(sysPrompt).contains("NEVER provide legal conclusions about patents, trademarks, or ownership")
    }
}
