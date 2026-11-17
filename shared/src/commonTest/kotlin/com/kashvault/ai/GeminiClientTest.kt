package com.kashvault.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiClientTest {
    private val client = GeminiClient()

    @Test
    fun currentModelsDoNotUseShutDownFlash20() {
        assertFalse(GEMINI_MODELS.contains("gemini-2.0-flash"))
        assertTrue(GEMINI_MODELS.contains("gemini-3.5-flash"))
        assertTrue(GEMINI_MODELS.contains("gemini-2.5-pro"))
    }

    @Test
    fun selectedModelIsTriedFirst() {
        assertEquals(
            listOf("gemini-2.5-pro", "gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.1-flash-lite"),
            geminiModelOrder("gemini-2.5-pro"),
        )
        assertEquals(GEMINI_MODELS.first(), normalizeGeminiModel("unknown-model"))
        assertEquals("gemini-2.5-flash", normalizeGeminiModel("gemini-2.5-flash"))
    }

    @Test
    fun stripJsonFenceRemovesMarkdown() {
        assertEquals(
            """{"amount":"50"}""",
            client.stripJsonFence("```json\n{\"amount\":\"50\"}\n```"),
        )
    }

    @Test
    fun geminiErrorUsesGoogleMessage() {
        val raw = """{"error":{"code":404,"message":"models/gemini-2.0-flash is not found","status":"NOT_FOUND"}}"""
        assertEquals(
            "Gemini model not found. models/gemini-2.0-flash is not found",
            geminiErrorMessage(raw, 404),
        )
    }

    @Test
    fun invalidKeyErrorKeepsGoogleDetail() {
        val raw = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertEquals(
            "API key not valid. Please pass a valid API key.",
            geminiErrorMessage(raw, 400),
        )
    }

    @Test
    fun preferredModelIsTriedFirstThenFallbacks() {
        assertEquals(
            listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-2.5-pro", "gemini-3.1-flash-lite"),
            geminiModelsToTry("gemini-2.5-flash"),
        )
        assertEquals(GEMINI_MODELS, geminiModelsToTry(" "))
        assertEquals(GEMINI_MODELS.first(), resolveGeminiModel(null))
        assertEquals("gemini-2.5-flash", resolveGeminiModel("gemini-2.5-flash"))
    }

    @Test
    fun timeoutErrorsUseLocalVaultMessage() {
        assertTrue(isGeminiTimeout(RuntimeException("socket timeout")))
        assertEquals(
            GEMINI_TIMEOUT_MESSAGE,
            geminiFailureMessage(GeminiException(GEMINI_TIMEOUT_MESSAGE)),
        )
        assertEquals(
            GEMINI_TIMEOUT_MESSAGE,
            geminiFailureMessage(IllegalStateException("timed out waiting for Gemini")),
        )
    }
}
