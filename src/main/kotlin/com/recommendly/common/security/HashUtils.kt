package com.recommendly.common.security

import java.security.MessageDigest

/**
 * Simple SHA-256 utility for hashing refresh tokens before storing them in the DB.
 *
 * We never store raw refresh tokens — if the DB were compromised, an attacker
 * would need to reverse SHA-256 to use the stolen data, which is computationally infeasible.
 */
object HashUtils {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
