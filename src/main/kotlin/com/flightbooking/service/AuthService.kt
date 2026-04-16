package com.flightbooking.service

import com.flightbooking.access.UserTableAccess
import org.mindrot.jbcrypt.BCrypt

object AuthService {
    private val users = UserTableAccess()

    fun register(email: String, password: String, firstName: String?, lastName: String?): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        val hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
        return users.createUser(email, hash, firstName, lastName)
    }

    fun login(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        val user = users.findByEmail(email) ?: return false
        val stored = user.passwordHash ?: return false
        return BCrypt.checkpw(password, stored)
    }
}
