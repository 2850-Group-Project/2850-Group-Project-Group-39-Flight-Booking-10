package com.flightbooking.routes

import com.flightbooking.models.StaffSession
import com.flightbooking.service.StaffAuthService
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

/**
 * Staff authentication routes (login + registration).
 *
 * Routes:
 * - GET  /staff/login -> Renders the staff login page.
 * - POST /staff/login -> Validates credentials via [StaffAuthService.login]. On success, stores [StaffSession]
 *   and redirects to `/staff/dashboard`. On failure, re-renders login with an error.
 *
 * - GET  /staff/register -> Renders the staff registration page.
 * - POST /staff/register -> Validates an invite code, then registers a staff account via
 *   [StaffAuthService.register]. On success, redirects to `/staff/login`. On failure, re-renders with an error.
 *
 * Security note:
 * - Invite-code validation is a shared-secret gate to reduce unauthorised staff account creation.
 */
fun Route.staffAuthRoutes() {
    get("/staff/login") {
        call.respond(PebbleContent("staff_login.peb", mapOf<String, Any>()))
    }

    post("/staff/login") {
        val params = call.receiveParameters()
        val email = params["email"]?.trim().orEmpty()
        val password = params["password"].orEmpty()

        if (StaffAuthService.login(email, password)) {
            call.sessions.set(StaffSession(staffEmail = email))
            call.respondRedirect("/staff/dashboard")
        } else {
            call.respond(PebbleContent("staff_login.peb", mapOf("error" to "Invalid staff credentials")))
        }
    }

    get("/staff/register") {
        call.respond(PebbleContent("staff_register.peb", mapOf<String, Any>()))
    }

    post("/staff/register") {
        val params = call.receiveParameters()
        val firstName = params["firstName"]?.trim()
        val lastName = params["lastName"]?.trim()
        val email = params["email"]?.trim().orEmpty()
        val password = params["password"].orEmpty()
        val confirmPassword = params["confirmPassword"].orEmpty()
        val role = params["role"]?.trim()
        val inviteCode = params["inviteCode"]?.trim().orEmpty()
        val expectedInvite = "STAFF-CHECK"

        if (password != confirmPassword) {
            call.respond(
                PebbleContent(
                    "staff_register.peb",
                    mapOf("error" to "Passwords do not match"),
                ),
            )
            return@post
        }

        if (inviteCode != expectedInvite) {
            call.respond(PebbleContent("staff_register.peb", mapOf("error" to "Invalid invite code")))
            return@post
        }
        if (StaffAuthService.register(email, password, firstName, lastName, role)) {
            call.respondRedirect("/staff/login")
        } else {
            call.respond(PebbleContent("staff_register.peb", mapOf("error" to "Staff already exists")))
        }
    }
}
