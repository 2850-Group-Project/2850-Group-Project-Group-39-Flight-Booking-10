package com.flightbooking.service

import com.flightbooking.access.UserTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.UserTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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

    suspend fun requireUser(call: ApplicationCall): UserSession? {
        val userSession = call.sessions.get<UserSession>()

        val userId = userSession?.let { fetchValidUserId(it.userEmail) }

        if (userId == null || userSession == null) {
            call.respondRedirect("/login")
            return null
        }

        return userSession
    }

    suspend fun requireBooking(call: ApplicationCall): BookingSession? {
        val bookingSession = call.sessions.get<BookingSession>()

        if (bookingSession == null) {
            call.respondRedirect("/home")
            return null
        }

        return bookingSession
    }

    private fun fetchValidUserId(userEmail: String): Int? =
        transaction {
            UserTable
                .select { UserTable.email eq userEmail }
                .singleOrNull()
                ?.get(UserTable.id)
        }
}
