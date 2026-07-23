package com.recommendly.common.security

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Wraps BCrypt so the rest of the codebase never touches raw hashing logic.
 *
 * Cost factor 12 is the industry standard for 2024+:
 * - Adds ~300ms per hash on modern hardware (acceptable at login/register)
 * - Makes brute-force attacks computationally expensive
 * - Not too slow to cause timeout issues under load
 */
class PasswordService {

    private val cost = 12

    /** Returns a BCrypt hash of the plaintext password. */
    fun hash(plaintext: String): String =
        BCrypt.withDefaults().hashToString(cost, plaintext.toCharArray())

    /** Returns true if plaintext matches the stored hash. */
    fun verify(plaintext: String, hash: String): Boolean =
        BCrypt.verifyer()
            .verify(plaintext.toCharArray(), hash)
            .verified
}
