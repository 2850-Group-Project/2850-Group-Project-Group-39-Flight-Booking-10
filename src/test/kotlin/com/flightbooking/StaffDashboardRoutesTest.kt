package com.flightbooking

import com.flightbooking.tables.StaffTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaffDashboardRoutesTest : IntegrationTestSupport() {
    // Unauthenticated staff users should be redirected to the staff login page.
    @Test
    fun unauthenticatedDashboardRedirectsToStaffLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/staff/dashboard")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/staff/login", response.headers[HttpHeaders.Location])
        }

    // An authenticated staff user should be able to load the dashboard page.
    @Test
    fun authenticatedDashboardLoads() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            val response = client.get("/staff/dashboard")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Staff Dashboard"))
        }

    // A stale staff session should show a staff-not-found response.
    @Test
    fun dashboardShowsStaffNotFoundMessageWhenSessionUserIsMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()

            deleteStaffByEmail("staff@example.com")

            val response = client.get("/staff/dashboard")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Staff not found, please login again."))
        }

    // Remove the backing staff row to simulate a stale dashboard session.
    private fun deleteStaffByEmail(email: String) =
        transaction {
            StaffTable.deleteWhere { StaffTable.email eq email }
        }
}
