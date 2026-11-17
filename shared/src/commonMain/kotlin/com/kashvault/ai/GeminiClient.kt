package com.kashvault.ai

import com.kashvault.util.LiquidityForecastOutput
import com.kashvault.util.LiquidityInput
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val GEMINI_REQUEST_TIMEOUT_MS = 30_000L
const val GEMINI_CONNECT_TIMEOUT_MS = 15_000L
const val GEMINI_SOCKET_TIMEOUT_MS = 30_000L
const val GEMINI_TIMEOUT_MESSAGE = "Request timed out. Please try again."

class GeminiException(message: String) : Exception(message)

data class GeminiModelOption(
    val id: String,
    val label: String,
)

val GEMINI_MODEL_OPTIONS = listOf(
    GeminiModelOption("gemini-3.5-flash", "3.5 Flash"),
    GeminiModelOption("gemini-2.5-flash", "2.5 Flash"),
    GeminiModelOption("gemini-2.5-pro", "2.5 Pro"),
    GeminiModelOption("gemini-3.1-flash-lite", "3.1 Lite"),
)

val GEMINI_MODELS = GEMINI_MODEL_OPTIONS.map { it.id }

fun normalizeGeminiModel(id: String?): String {
    val trimmed = id?.trim().orEmpty()
    return GEMINI_MODELS.firstOrNull { it == trimmed } ?: GEMINI_MODELS.first()
}

internal fun geminiModelOrder(preferred: String?): List<String> {
    val chosen = normalizeGeminiModel(preferred)
    return listOf(chosen) + GEMINI_MODELS.filter { it != chosen }
}

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null,
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
internal data class GeminiErrorEnvelope(
    val error: GeminiErrorBody? = null,
)

@Serializable
internal data class GeminiErrorBody(
    val message: String? = null,
    val status: String? = null,
    val code: Int? = null,
)

@Serializable
private data class LiquidityForecastDto(
    val forecast: String,
    val riskLevel: String,
    val runwayDays: Int,
    val tips: List<String> = emptyList(),
)

class GeminiClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = GEMINI_REQUEST_TIMEOUT_MS
            connectTimeoutMillis = GEMINI_CONNECT_TIMEOUT_MS
            socketTimeoutMillis = GEMINI_SOCKET_TIMEOUT_MS
        }
    }

    suspend fun generateText(
        apiKey: String,
        prompt: String,
        systemContext: String? = null,
        preferredModel: String? = null,
    ): String? {
        val key = apiKey.trim()
        if (key.isBlank()) return null

        var lastTimeout: String? = null
        var lastNotFound: String? = null
        geminiModelOrder(preferredModel).forEach { model ->
            val result = requestModel(key, model, prompt, systemContext)
            when {
                !result.text.isNullOrBlank() -> return result.text
                result.timedOut -> lastTimeout = result.error
                result.notFound -> lastNotFound = result.error
                else -> throw GeminiException(result.error ?: "Gemini request failed")
            }
        }
        if (lastTimeout != null) throw GeminiException(lastTimeout)
        throw GeminiException(lastNotFound ?: "No supported Gemini model is available.")
    }

    private suspend fun requestModel(
        apiKey: String,
        model: String,
        prompt: String,
        systemContext: String?,
    ): ModelCall {
        val response = try {
            client.post(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            ) {
                timeout {
                    requestTimeoutMillis = GEMINI_REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = GEMINI_CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = GEMINI_SOCKET_TIMEOUT_MS
                }
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(prompt)))),
                        systemInstruction = systemContext?.takeIf { it.isNotBlank() }?.let {
                            GeminiContent(parts = listOf(GeminiPart(it)))
                        },
                    ),
                )
            }
        } catch (error: Throwable) {
            if (isGeminiTimeout(error)) {
                return ModelCall(error = GEMINI_TIMEOUT_MESSAGE, timedOut = true)
            }
            throw error
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return ModelCall(
                error = geminiErrorMessage(raw, response.status.value),
                notFound = response.status.value == 404,
            )
        }
        val body = runCatching { json.decodeFromString<GeminiResponse>(raw) }.getOrNull()
        val text = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        return if (text.isNullOrBlank()) {
            ModelCall(error = "Gemini returned an empty answer.")
        } else {
            ModelCall(text = text)
        }
    }

    fun stripJsonFence(raw: String): String =
        raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    suspend fun forecastLiquidity(
        apiKey: String,
        input: LiquidityInput,
        preferredModel: String? = null,
    ): LiquidityForecastOutput? {
        val prompt = """
            You are KashVault Predictive Intelligence. Analyze the following liquidity profile and return only JSON exactly in this format:
            {
              "forecast": "string",
              "riskLevel": "Low|Medium|High",
              "runwayDays": 0,
              "tips": ["string"]
            }

            Current Cash: ₹${input.currentLiquidity}
            Avg Monthly Spend: ₹${input.monthlyAvgSpend}
            Est Monthly Income: ₹${input.monthlyIncome}
            Upcoming Fixed Bills (Lien): ₹${input.upcomingLien}

            Provide a 30-day forecast, calculate runway days, assess risk level, and offer concise cash flow recommendations. Use only JSON in your response.
        """.trimIndent()

        val result = generateText(apiKey, prompt, preferredModel = preferredModel) ?: return null
        return try {
            val dto = json.decodeFromString<LiquidityForecastDto>(stripJsonFence(result))
            LiquidityForecastOutput(dto.forecast, dto.riskLevel, dto.runwayDays, dto.tips)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun parseExpense(
        apiKey: String,
        text: String,
        categories: List<Pair<String, String>>,
        sources: List<Pair<String, String>> = emptyList(),
        preferredModel: String? = null,
    ): Map<String, String>? {
        val categoryList = categories.joinToString("\n") { "- ${it.first}: ${it.second}" }
        val sourceList = if (sources.isEmpty()) {
            "No payment sources. Omit sourceId."
        } else {
            sources.joinToString("\n") { "- ${it.first}: ${it.second}" }
        }
        val prompt = """
            Parse this expense/income into JSON with keys: amount, categoryId, note, type (expense or income), sourceId.
            sourceId is the bank or credit card that paid (expense) or received (income). Use null if unknown.
            Available categories:
            $categoryList
            Available payment sources:
            $sourceList
            Input: "$text"
            Return only valid JSON.
        """.trimIndent()

        val result = generateText(apiKey, prompt, preferredModel = preferredModel) ?: return null
        return try {
            json.decodeFromString<Map<String, String>>(stripJsonFence(result))
        } catch (_: Exception) {
            null
        }
    }
}

fun geminiModelsToTry(preferredModel: String?): List<String> {
    val preferred = preferredModel?.trim()?.takeIf { it.isNotEmpty() }
    return (listOfNotNull(preferred) + GEMINI_MODELS).distinct()
}

fun resolveGeminiModel(stored: String?): String {
    val trimmed = stored?.trim().orEmpty()
    return if (trimmed in GEMINI_MODELS) trimmed else GEMINI_MODELS.first()
}

fun geminiModelLabel(id: String): String =
    GEMINI_MODEL_OPTIONS.firstOrNull { it.id == id }?.label ?: id

fun isGeminiTimeout(error: Throwable): Boolean {
    if (error is HttpRequestTimeoutException ||
        error is SocketTimeoutException ||
        error is ConnectTimeoutException
    ) {
        return true
    }
    val text = buildString {
        append(error::class.simpleName.orEmpty())
        append(' ')
        append(error.message.orEmpty())
        error.cause?.let {
            append(' ')
            append(it::class.simpleName.orEmpty())
            append(' ')
            append(it.message.orEmpty())
        }
    }.lowercase()
    return text.contains("timeout") || text.contains("timed out")
}

fun geminiFailureMessage(error: Throwable): String {
    if (error is GeminiException && error.message == GEMINI_TIMEOUT_MESSAGE) return GEMINI_TIMEOUT_MESSAGE
    if (isGeminiTimeout(error)) return GEMINI_TIMEOUT_MESSAGE
    return error.message?.trim()?.takeIf { it.isNotEmpty() } ?: "Gemini request failed"
}

private val geminiJson = Json { ignoreUnknownKeys = true }

internal fun geminiErrorMessage(raw: String, status: Int): String {
    val parsed = runCatching {
        geminiJson.decodeFromString<GeminiErrorEnvelope>(raw).error
    }.getOrNull()
    val detail = parsed?.message?.trim()?.takeIf { it.isNotEmpty() }
        ?: parsed?.status?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        detail != null && status == 404 -> "Gemini model not found. $detail"
        detail != null -> detail
        status == 404 -> "Gemini model not found ($status)."
        status == 400 || status == 403 -> "Gemini rejected the API key ($status)."
        else -> "Gemini request failed ($status)."
    }
}

private data class ModelCall(
    val text: String? = null,
    val error: String? = null,
    val notFound: Boolean = false,
    val timedOut: Boolean = false,
)
