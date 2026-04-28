package com.flightbooking.service

import com.flightbooking.access.StaffTableAccess
import org.mindrot.jbcrypt.BCrypt

object StaffAuthService {
    private val staffAccess = StaffTableAccess()

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
