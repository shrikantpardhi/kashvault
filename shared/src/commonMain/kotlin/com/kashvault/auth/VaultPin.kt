package com.kashvault.auth

fun isValidVaultPin(pin: String): Boolean =
    pin.length in 4..8 && pin.all { it.isDigit() }

fun newVaultPinSalt(): String = toHex(secureRandomBytes(16))

fun hashVaultPin(pin: String, saltHex: String): String =
    toHex(sha256("$saltHex:$pin".encodeToByteArray()))

fun vaultPinMatches(pin: String, saltHex: String, expectedHash: String): Boolean {
    if (saltHex.isBlank() || expectedHash.isBlank()) return false
    val actual = hashVaultPin(pin, saltHex)
    if (actual.length != expectedHash.length) return false
    var mismatch = 0
    actual.indices.forEach { index ->
        mismatch = mismatch or (actual[index].code xor expectedHash[index].code)
    }
    return mismatch == 0
}

private fun toHex(bytes: ByteArray): String =
    bytes.joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
