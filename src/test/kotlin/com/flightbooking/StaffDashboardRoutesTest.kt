package com.flightbooking

import com.flightbooking.tables.StaffTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaffDashboardRoutesTest : IntegrationTestSupport() {
    @Test
    // Unauthenticated staff users should be redirected to the staff login page.
    fun unauthenticatedDashboardRedirectsToStaffLogin() = testApplication {
        configureApp()
        val client = createClient { followRedirects = false }

        val response = client.get("/staff/dashboard")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/login", response.headers[HttpHeaders.Location])
    }

    @Test
    // An authenticated staff user should be able to load the dashboard page.
    fun authenticatedDashboardLoads() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)
        assertEquals("/staff/dashboard", loginResponse.headers[HttpHeaders.Location])

        val response = client.get("/staff/dashboard")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Staff Dashboard"))
    }

    @Test
    // A stale staff session should show a staff-not-found response.
    fun dashboardShowsStaffNotFoundMessageWhenSessionUserIsMissing() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        deleteStaffByEmail("staff@example.com")

        val response = client.get("/staff/dashboard")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Staff not found, please login again."))
    }

    // Submit a valid staff registration form for staff dashboard tests.
    private suspend fun HttpClient.registerStaff(
        email: String = "staff@example.com",
        password: String = "StrongPass123!"
    ) = submitForm(
        url = "/staff/register",
        formParameters = parameters {
            append("firstName", "Alex")
            append("lastName", "Admin")
            append("email", email)
            append("password", password)
            append("confirmPassword", password)
            append("role", "admin")
            append("inviteCode", "STAFF-CHECK")
        }
    )

    // Submit a staff login form for authenticated dashboard requests.
    private suspend fun HttpClient.loginStaff(
        email: String = "staff@example.com",
        password: String = "StrongPass123!"
    ) = submitForm(
        url = "/staff/login",
        formParameters = parameters {
            append("email", email)
            append("password", password)
        }
    )

    // Remove the backing staff row to simulate a stale dashboard session.
    private fun deleteStaffByEmail(email: String) = transaction {
        StaffTable.deleteWhere { StaffTable.email eq email }
    }
}
