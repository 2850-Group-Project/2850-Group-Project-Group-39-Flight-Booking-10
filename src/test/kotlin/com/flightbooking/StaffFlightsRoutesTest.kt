package com.flightbooking

import com.flightbooking.tables.AirportTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.SeatTable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaffFlightsRoutesTest : IntegrationTestSupport() {
    @Test
    // Unauthenticated staff users should be sent to the staff login page.
    fun unauthenticatedStaffFlightsRedirectsToStaffLogin() = testApplication {
        configureApp()
        val client = createClient { followRedirects = false }

        val response = client.get("/staff/flights")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/login", response.headers[HttpHeaders.Location])
    }

    @Test
    // An authenticated staff user should be able to load the staff flights page.
    fun authenticatedStaffFlightsPageLoads() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)
        assertEquals("/staff/dashboard", loginResponse.headers[HttpHeaders.Location])

        val response = client.get("/staff/flights")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Flight Scheduler"))
    }

    @Test
    // Staff should be able to create a flight from the management page.
    fun createFlightRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val response = client.submitForm(
            url = "/staff/flights/create",
            formParameters = parameters {
                append("flightNumber", "101")
                append("originId", originAirportId.toString())
                append("destId", destinationAirportId.toString())
                append("dep", "2026-04-01 09:00:00")
                append("arr", "2026-04-01 11:00:00")
                append("status", "scheduled")
                append("capacity", "180")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/flights?ok=Flight created", response.headers[HttpHeaders.Location])
    }

    @Test
    // Flight creation should reject invalid route input and redirect with an error.
    fun createFlightRejectsInvalidRouteData() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")
        val airportId = seedAirport("LHR", "London Heathrow")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val response = client.submitForm(
            url = "/staff/flights/create",
            formParameters = parameters {
                append("flightNumber", "101")
                append("originId", airportId.toString())
                append("destId", airportId.toString())
                append("dep", "2026-04-01 09:00:00")
                append("arr", "2026-04-01 11:00:00")
                append("status", "scheduled")
                append("capacity", "180")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/staff/flights?error=Origin and destination cannot be the same",
            response.headers[HttpHeaders.Location]
        )
    }

    @Test
    // Staff should be able to update an existing flight.
    fun updateFlightRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val createResponse = client.submitForm(
            url = "/staff/flights/create",
            formParameters = parameters {
                append("flightNumber", "303")
                append("originId", originAirportId.toString())
                append("destId", destinationAirportId.toString())
                append("dep", "2026-04-03 09:00:00")
                append("arr", "2026-04-03 11:00:00")
                append("status", "scheduled")
                append("capacity", "12")
            }
        )
        assertEquals(HttpStatusCode.Found, createResponse.status)

        val flightId = latestFlightId()
        val response = client.submitForm(
            url = "/staff/flights/update",
            formParameters = parameters {
                append("id", flightId.toString())
                append("flightNumber", "404")
                append("originId", destinationAirportId.toString())
                append("destId", originAirportId.toString())
                append("dep", "2026-04-04 13:30:00")
                append("arr", "2026-04-04 20:45:00")
                append("status", "delayed")
                append("capacity", "16")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/flights?ok=Flight updated", response.headers[HttpHeaders.Location])
    }

    @Test
    // Staff should be able to delete an existing flight.
    fun deleteFlightRedirectsWithSuccessMessage() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val createResponse = client.submitForm(
            url = "/staff/flights/create",
            formParameters = parameters {
                append("flightNumber", "505")
                append("originId", originAirportId.toString())
                append("destId", destinationAirportId.toString())
                append("dep", "2026-04-05 09:00:00")
                append("arr", "2026-04-05 11:00:00")
                append("status", "scheduled")
                append("capacity", "20")
            }
        )
        assertEquals(HttpStatusCode.Found, createResponse.status)

        val flightId = latestFlightId()
        val response = client.submitForm(
            url = "/staff/flights/delete",
            formParameters = parameters {
                append("id", flightId.toString())
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/staff/flights?ok=Flight deleted", response.headers[HttpHeaders.Location])
    }

    @Test
    // Creating a flight should automatically generate seats for that flight.
    fun createFlightAutoGeneratesSeats() = testApplication {
        configureApp()
        val client = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        client.get("/__health")
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")

        client.registerStaff()
        val loginResponse = client.loginStaff()
        assertEquals(HttpStatusCode.Found, loginResponse.status)

        val response = client.submitForm(
            url = "/staff/flights/create",
            formParameters = parameters {
                append("flightNumber", "202")
                append("originId", originAirportId.toString())
                append("destId", destinationAirportId.toString())
                append("dep", "2026-04-02 09:00:00")
                append("arr", "2026-04-02 11:00:00")
                append("status", "scheduled")
                append("capacity", "8")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)

        val newestFlightId = latestFlightId()
        val seatCodes = seatCodesForFlight(newestFlightId)

        assertEquals(8, seatCodes.size)
        assertEquals("1A", seatCodes.first())
        assertEquals("2B", seatCodes.last())
    }

    // Submit a valid staff registration form for staff flights tests.
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

    // Submit a staff login form for authenticated staff flights requests.
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

    // Insert an airport row so flight-management tests can submit valid airport ids.
    private fun seedAirport(iataCode: String, name: String): Int = transaction {
        AirportTable.insert {
            it[AirportTable.iataCode] = iataCode
            it[AirportTable.name] = name
            it[city] = null
            it[country] = null
        }.resultedValues!!.first()[AirportTable.id]
    }

    // Fetch the most recently created flight id for post-create assertions.
    private fun latestFlightId(): Int = transaction {
        FlightTable
            .selectAll()
            .orderBy(FlightTable.id, SortOrder.DESC)
            .limit(1)
            .first()[FlightTable.id]
    }

    // Read generated seat codes for a flight in creation order.
    private fun seatCodesForFlight(flightId: Int): List<String> = transaction {
        SeatTable
            .select { SeatTable.flightId eq flightId }
            .orderBy(SeatTable.id, SortOrder.ASC)
            .map { it[SeatTable.seatCode] }
    }
}
