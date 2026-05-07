package com.flightbooking.service

import com.flightbooking.access.UserTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.StaffSession
import com.flightbooking.models.UserSession
import com.flightbooking.tables.StaffTable
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
     * @param email
     * @param password
     * @param firstName
     * @param lastName
     * @return false if email or password is blank, true if registration successful
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
     * @param email
     * @param password
     * @return true if login successful, false if not
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

    /**
     * Checks for user session
     * @param call: request call
     * @return the user session and userId
     */
    suspend fun requireUser(call: ApplicationCall): Pair<UserSession, Int>? {
        val userSession = call.sessions.get<UserSession>()

        val userId = userSession?.let { fetchValidUserId(it.userEmail) }

        if (userSession == null || userId == null) {
            call.respondRedirect("/login")
            return null
        }

        return Pair(userSession, userId)
    }

    /**
     * Checks for staff session
     * @param call: request call
     * @return the staff session and userId
     */
    suspend fun requireStaff(call: ApplicationCall): Pair<StaffSession, Int>? {
        val staffSession = call.sessions.get<StaffSession>()

        val staffId = staffSession?.let { fetchValidStaffId(it.staffEmail) }

        if (staffSession == null || staffId == null) {
            call.respondRedirect("/staff/login")
            return null
        }

        return Pair(staffSession, staffId)
    }

    /**
     * Checks for booking session
     * @param call: request call
     * @return the booking session and userId
     */
    suspend fun requireBooking(
        call: ApplicationCall,
        requireSearch: Boolean = false,
    ): BookingSession? {
        val bookingSession = call.sessions.get<BookingSession>()
        val search = bookingSession?.search

        if (bookingSession == null || (requireSearch && search == null)) {
            call.respondRedirect("/home")
            return null
        }

        return bookingSession
    }

    /**
     * Searches User table for userId with email
     * @param userEmail
     * @return userId
     */
    private fun fetchValidUserId(userEmail: String): Int? =
        transaction {
            UserTable
                .select { UserTable.email eq userEmail }
                .singleOrNull()
                ?.get(UserTable.id)
        }

    /**
     * Searches Staff table for staffId with email
     * @param staffEmail
     * @return staffId
     */
    private fun fetchValidStaffId(staffEmail: String): Int? =
        transaction {
            StaffTable
                .select { StaffTable.email eq staffEmail }
                .singleOrNull()
                ?.get(StaffTable.id)
        }
}
