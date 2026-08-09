package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {
    @Test
    fun scoreRewardsExactPhrasePositionAndStillAllowsUsefulTypos() {
        val start = FuzzySearch.score("Amazon payment completed", "amazon")
        val middle = FuzzySearch.score("Paid at Amazon today", "amazon")

        assertTrue(start > middle)
        assertTrue(middle > 0)
        assertTrue(FuzzySearch.score("verification", "vrfctn") > 0)
        assertEquals(0.0, FuzzySearch.score("unrelated", ""), 0.0)
        assertEquals(0.0, FuzzySearch.score("unrelated", "xyz"), 0.0)
    }

    @Test
    fun searchPrioritizesContactsThenBodyAndUsesDateAsTieBreaker() {
        val messages = listOf(
            result(1, "VK-AMAZN-T", "Amazon", "Package shipped", 100),
            result(2, "VM-BANK-T", null, "Amazon payment completed", 300),
            result(3, "VK-AMAZN-T", "Amazon Pay", "Wallet update", 200),
            result(4, "OTHER", null, "Nothing relevant", 400),
        )

        assertEquals(listOf(1L, 3L, 2L), FuzzySearch.search(messages, "Amazon").map { it.messageId })
        assertEquals(listOf(4L, 2L, 3L, 1L), FuzzySearch.search(messages, " ").map { it.messageId })
    }

    @Test
    fun multiWordSearchSupportsOutOfOrderAndPartialMatches() {
        val complete = FuzzySearch.score("Your card payment was completed", "payment card")
        val partial = FuzzySearch.score("Your card was updated", "payment card")

        assertTrue(complete > partial)
        assertTrue(partial > 0)
    }

    private fun result(
        id: Long,
        sender: String,
        display: String?,
        body: String,
        date: Long,
    ) = SearchResultItem(
        messageId = id,
        threadId = id,
        sender = sender,
        senderDisplay = display,
        body = body,
        date = date,
        contactPhotoUri = null,
        contactLookupUri = null,
        category = MessageCategory.UNKNOWN,
    )
}
