package com.flightbooking.service

import com.flightbooking.access.StaffTableAccess
import org.mindrot.jbcrypt.BCrypt

/**
 * Service object for staff authentication
 */
object StaffAuthService {
    private val staffAccess = StaffTableAccess()

    /**
     * Registers a new staff member with email and password
     * Parameters: email, password, firstName, lastName, role
     * Returns: true if registration successful, false if email or password is blank
     */
    fun register(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        role: String?,
    ): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        return staffAccess.createStaff(email, hash, firstName, lastName, role)
    }

    /**
     * Logs in a staff member with email and password
     * Parameters: email, password
     * Returns: true if password correct, false if email not found or password wrong
     */
    fun login(
        email: String,
        password: String,
    ): Boolean {
        val storedHash =
            staffAccess.findByEmail(email)
                ?.passwordHash
                ?.takeIf { email.isNotBlank() && password.isNotBlank() }
                ?: return false
        return BCrypt.checkpw(password, storedHash)
    }
}
