package com.kashvault.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal object Pkce {
    @OptIn(ExperimentalEncodingApi::class)
    fun verifier(): String = base64UrlNoPad(secureRandomBytes(32))

    @OptIn(ExperimentalEncodingApi::class)
    fun challengeS256(verifier: String): String = base64UrlNoPad(sha256(verifier.encodeToByteArray()))

    @OptIn(ExperimentalEncodingApi::class)
    fun base64UrlNoPad(bytes: ByteArray): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
}

expect fun sha256(data: ByteArray): ByteArray

internal expect fun secureRandomBytes(size: Int): ByteArray
