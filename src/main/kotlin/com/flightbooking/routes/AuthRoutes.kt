package com.flightbooking.routes

import com.flightbooking.access.UserTableAccess
import com.flightbooking.models.UserSession
import com.flightbooking.service.AuthService
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

/**
 * Authentication routes for user registration, login, and logout.
 *
 * Routes:
 * - GET  /register  -> renders the user registration page
 * - POST /register  -> creates a new user account
 * - GET  /login     -> renders the user login page
 * - POST /login     -> authenticates a user and sets a session
 * - GET  /logout    -> clears the session and redirects to landing page
 */
fun Route.authRoutes() {
    get("/register") {
        call.respond(PebbleContent("register.peb", mapOf()))
    }
    post("/register") {
        val params = call.receiveParameters()
        val email = params["email"]?.trim().orEmpty()
        val password = params["password"].orEmpty()
        val confirmPassword = params["confirmPassword"].orEmpty()
        val firstName = params["firstName"]?.trim()
        val lastName = params["lastName"]?.trim()

        if (password != confirmPassword) {
            call.respond(
                PebbleContent(
                    "register.peb",
                    mapOf("error" to "Passwords do not match"),
                ),
            )
            return@post
        }

        if (AuthService.register(email, password, firstName, lastName)) {
            call.respondRedirect("/login")
        } else {
            call.respond(PebbleContent("register.peb", mapOf("error" to "User already exists")))
        }
    }
    get("/login") {
        call.respond(PebbleContent("login.peb", mapOf()))
    }
    post("/login") {
        val params = call.receiveParameters()
        val email = params["email"] ?: ""
        val password = params["password"] ?: ""
        if (AuthService.login(email, password)) {
            val userTable = UserTableAccess()
            val userData = userTable.findByEmail(email)

            print(userData)

            if (userData == null) {
                call.respondRedirect("/login")
                return@post
            }

            call.sessions.set(
                UserSession(
                    userEmail = email,
                    firstName = userData.firstName ?: "UNKNOWN",
                ),
            )
            call.respondRedirect("/home")
        } else {
            call.respond(PebbleContent("login.peb", mapOf("error" to "Invalid credentials")))
        }
    }

    get("/logout") {
        call.sessions.clear<UserSession>()
        println("logged out")
        call.respondRedirect("/")
    }
}
