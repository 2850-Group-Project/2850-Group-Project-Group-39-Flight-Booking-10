package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.StaffTable
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // Dashboard should show the count of flights that are not cancelled.
    @Test
    fun dashboardShowsActiveFlightCount() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val airports = seedDashboardAirports()
            seedDashboardFlight(airports, flightNumber = 701, status = "scheduled")
            seedDashboardFlight(airports, flightNumber = 702, status = "delayed")
            seedDashboardFlight(airports, flightNumber = 703, status = "cancelled")

            val response = client.get("/staff/dashboard")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertDashboardKpi(body, "Active Flights", "2")
        }

    // Dashboard should show how many flights depart today.
    @Test
    fun dashboardShowsDeparturesTodayCount() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val airports = seedDashboardAirports()
            val today = LocalDate.now()
            seedDashboardFlight(airports, flightNumber = 711, departureDate = today)
            seedDashboardFlight(airports, flightNumber = 712, departureDate = today)
            seedDashboardFlight(airports, flightNumber = 713, departureDate = today.plusDays(1))

            val response = client.get("/staff/dashboard")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertDashboardKpi(body, "Departures Today", "2")
        }

    // Dashboard should show the count of open customer complaints.
    @Test
    fun dashboardShowsOpenComplaintCount() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val userId = seedUser("dashboard-user@example.com", "Dana", "Customer")
            seedComplaint(userId, "service", "Open complaint one")
            seedComplaint(userId, "technical", "Open complaint two")
            val closedComplaintId = seedComplaint(userId, "service", "Closed complaint")
            updateComplaintStatus(closedComplaintId, "closed")

            val response = client.get("/staff/dashboard")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertDashboardKpi(body, "Customer Inquiries", "2")
        }

    // Dashboard should list active flights in the outbound flights table.
    @Test
    fun dashboardListsActiveFlights() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val airports = seedDashboardAirports()
            seedDashboardFlight(airports, flightNumber = 721, status = "scheduled")
            seedDashboardFlight(airports, flightNumber = 722, status = "boarding")

            val response = client.get("/staff/dashboard")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Upcoming Outbound Flights"))
            assertTrue(body.contains(">721<"))
            assertTrue(body.contains(">722<"))
            assertTrue(body.contains("Dubai (DXB)"))
        }

    // Cancelled flights should not appear in the active dashboard list.
    @Test
    fun dashboardExcludesCancelledFlightsFromActiveList() =
        testApplication {
            configureApp()
            val client = createAuthenticatedStaffClient()
            val airports = seedDashboardAirports()
            seedDashboardFlight(airports, flightNumber = 731, status = "scheduled")
            seedDashboardFlight(airports, flightNumber = 732, status = "cancelled")

            val response = client.get("/staff/dashboard")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains(">731<"))
            assertFalse(body.contains(">732<"))
            assertFalse(body.contains(">cancelled<"))
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

    private fun seedDashboardAirports(): DashboardAirports {
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")

        transaction {
            AirportTable.update({ AirportTable.id eq originAirportId }) {
                it[city] = "London"
            }
            AirportTable.update({ AirportTable.id eq destinationAirportId }) {
                it[city] = "Dubai"
            }
        }

        return DashboardAirports(originAirportId, destinationAirportId)
    }

    private fun seedDashboardFlight(
        airports: DashboardAirports,
        flightNumber: Int,
        status: String = "scheduled",
        departureDate: LocalDate = LocalDate.now(),
    ): Int {
        val flightId =
            seedFlight(
                originAirportId = airports.originAirportId,
                destinationAirportId = airports.destinationAirportId,
                flightNumberValue = flightNumber,
            )
        val departureTime = "$departureDate 09:00:00"
        val arrivalTime = "$departureDate 17:00:00"

        transaction {
            FlightTable.update({ FlightTable.id eq flightId }) {
                it[scheduledDepartureTime] = departureTime
                it[scheduledArrivalTime] = arrivalTime
                it[FlightTable.status] = status
            }
        }

        return flightId
    }

    private fun updateComplaintStatus(
        complaintId: Int,
        status: String,
    ) {
        transaction {
            ComplaintTable.update({ ComplaintTable.id eq complaintId }) {
                it[ComplaintTable.status] = status
            }
        }
    }

    private fun assertDashboardKpi(
        body: String,
        label: String,
        value: String,
    ) {
        val kpiPattern =
            Regex(
                "<span>$label</span>\\s*<b>$value</b>",
                RegexOption.DOT_MATCHES_ALL,
            )

        assertTrue(kpiPattern.containsMatchIn(body))
    }

    private data class DashboardAirports(
        val originAirportId: Int,
        val destinationAirportId: Int,
    )
}
