package com.kashvault.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultPinTest {
    @Test
    fun pinMustBeFourToEightDigits() {
        assertTrue(isValidVaultPin("1234"))
        assertTrue(isValidVaultPin("12345678"))
        assertFalse(isValidVaultPin("123"))
        assertFalse(isValidVaultPin("123456789"))
        assertFalse(isValidVaultPin("12ab"))
    }

    @Test
    fun hashRoundTrip() {
        val salt = newVaultPinSalt()
        val hash = hashVaultPin("4820", salt)
        assertTrue(vaultPinMatches("4820", salt, hash))
        assertFalse(vaultPinMatches("4821", salt, hash))
        assertFalse(vaultPinMatches("4820", "00", hash))
    }
}
