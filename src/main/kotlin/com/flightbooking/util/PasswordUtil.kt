package com.flightbooking.util

import org.mindrot.jbcrypt.BCrypt

/**
 * Utility for hashing and verifying passwords using [BCrypt].
 */
object PasswordUtil {
    /**
     * Returns a BCrypt salted hash of [password].
     * @param password
     * @return hashed password
     */
    fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    /**
     * Returns true if [password] matches the given BCrypt [hash].
     * @param password
     * @param hash
     * @return true if password matches hash
     */
    fun verify(
        password: String,
        hash: String,
    ): Boolean {
        return BCrypt.checkpw(password, hash)
    }
}
