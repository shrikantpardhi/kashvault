package com.kashvault.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun stripsBoldMarkers() {
        val parsed = parseStyledMarkdown("Cut **dining** this month.")
        assertEquals("Cut dining this month.", parsed.text)
        assertTrue(parsed.styles.any { it.bold && parsed.text.substring(it.start, it.end) == "dining" })
    }

    @Test
    fun rendersHeadingsAndBulletsWithoutHashes() {
        val parsed = parseStyledMarkdown(
            """
            ## Plan
            - Eat in
            - Skip cabs
            1. Track spend
            """.trimIndent(),
        )
        assertEquals("Plan\n• Eat in\n• Skip cabs\n• Track spend", parsed.text)
        assertTrue(parsed.styles.any { it.heading == 2 })
        assertFalseHash(parsed.text)
    }

    @Test
    fun inlineCodeAndLinksDropSyntax() {
        val parsed = parseStyledMarkdown("Use `UPI` via [HDFC](https://example.com).")
        assertEquals("Use UPI via HDFC.", parsed.text)
        assertTrue(parsed.styles.any { it.code && parsed.text.substring(it.start, it.end) == "UPI" })
    }

    private fun assertFalseHash(text: String) {
        assertTrue('#' !in text)
        assertTrue("**" !in text)
    }
}
