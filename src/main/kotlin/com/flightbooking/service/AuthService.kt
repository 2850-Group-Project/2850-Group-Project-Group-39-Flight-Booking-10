package com.flightbooking.service

import com.flightbooking.access.UserTableAccess
import org.mindrot.jbcrypt.BCrypt

/**
 * Service object for user authentication
 */
object AuthService {
    private val users = UserTableAccess()

    /**
     * Registers a user with email and password
     * Parameters: email, password, firstName, lastName
     * Returns: false if email or password is blank, true if registration successful
     */
    fun register(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
    ): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        val hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
        return users.createUser(email, hash, firstName, lastName)
    }

    /**
     * Logs in user with email and password
     * Parameters: email, password
     * Returns: true if login successful, false if not
     */
    fun login(
        email: String,
        password: String,
    ): Boolean {
        val storedHash =
            users.findByEmail(email)
                ?.passwordHash
                ?.takeIf { email.isNotBlank() && password.isNotBlank() }
                ?: return false
        return BCrypt.checkpw(password, storedHash)
    }
}
