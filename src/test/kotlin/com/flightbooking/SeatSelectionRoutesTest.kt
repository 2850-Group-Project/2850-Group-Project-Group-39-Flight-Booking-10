package com.flightbooking

import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.SeatAssignmentTable
import com.flightbooking.tables.SeatTable
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatSelectionRoutesTest : IntegrationTestSupport() {
    /**
     * Unauthenticated users should be redirected to login from seat selection.
     */
    @Test
    fun unauthenticatedSeatSelectionRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/flights/seats")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Unauthenticated seat submissions should be redirected to login.
     */
    @Test
    fun unauthenticatedSeatSubmitRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/flights/seats")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Authenticated users with a booking session should see the seat map.
     */
    @Test
    fun authenticatedSeatSelectionRendersSeatMap() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.get("/flights/seats")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Seat Selection"))
            assertTrue(body.contains("Passengers on this flight"))
            assertTrue(body.contains("data-seat-code=\"1A\""))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
        }

    /**
     * The seat map should include passengers already saved for the booking.
     */
    @Test
    fun seatMapIncludesPassengersFromBooking() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario(passengerCount = 2)
            client.setSeatBookingSession(scenario, adults = "2")

            val response = client.get("/flights/seats")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Alex Passenger"))
            assertTrue(body.contains("Casey Passenger"))
            assertTrue(body.contains("0 / 2"))
        }

    /**
     * Blank seat selections should redirect back with a clear error.
     */
    @Test
    fun blankSelectedSeatsRedirectsWithError() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("")

            assertEquals(HttpStatusCode.Found, response.status)
            assertRedirectError(response.headers[HttpHeaders.Location], "No seats selected")
        }

    /**
     * Malformed seat selection JSON should redirect back with a clear error.
     */
    @Test
    fun invalidSelectedSeatsJsonRedirectsWithError() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("{bad json")

            assertEquals(HttpStatusCode.Found, response.status)
            assertRedirectError(response.headers[HttpHeaders.Location], "Invalid seat selection format")
        }

    /**
     * Unknown seat codes should be rejected.
     */
    @Test
    fun unknownSeatCodeRedirectsWithError() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("""{"${scenario.passengerIds.first()}":"99Z"}""")

            assertEquals(HttpStatusCode.Found, response.status)
            assertRedirectError(response.headers[HttpHeaders.Location], "Seat 99Z not found")
        }

    /**
     * Occupied seats should be rejected.
     */
    @Test
    fun occupiedSeatRedirectsWithError() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario(occupiedSeatCodes = listOf("1A"))
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("""{"${scenario.passengerIds.first()}":"1A"}""")

            assertEquals(HttpStatusCode.Found, response.status)
            assertRedirectError(response.headers[HttpHeaders.Location], "Seat 1A is already occupied")
        }

    /**
     * Valid selections should create a booking segment and seat assignment.
     */
    @Test
    fun validSeatSelectionCreatesBookingSegmentAndSeatAssignment() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("""{"${scenario.passengerIds.first()}":"1A"}""")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/payment?ok=Seats assigned successfully", response.headers[HttpHeaders.Location])
            assertTrue(bookingSegmentExists(scenario.bookingId))
            assertEquals(scenario.seatIdsByCode["1A"], assignedSeatIdForBooking(scenario.bookingId))
        }

    /**
     * Valid selections should mark the selected seat occupied.
     */
    @Test
    fun validSeatSelectionMarksSelectedSeatOccupied() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario()
            client.setSeatBookingSession(scenario)

            val response = client.submitSeats("""{"${scenario.passengerIds.first()}":"1A"}""")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("occupied", seatStatus(scenario.seatIdsByCode.getValue("1A")))
        }

    /**
     * One booking with multiple passengers should submit separate seat choices together.
     */
    @Test
    fun multiplePassengersCanSelectSeats() =
        testApplication {
            configureApp()
            installSeatSessionRoute()
            val client = createAuthenticatedUserClient()
            val scenario = seedSeatSelectionScenario(passengerCount = 2)
            client.setSeatBookingSession(scenario, adults = "2")

            val response =
                client.submitSeats(
                    """{"${scenario.passengerIds[0]}":"1A","${scenario.passengerIds[1]}":"1B"}""",
                )

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals(2, seatAssignmentCountForBooking(scenario.bookingId))
            assertEquals("occupied", seatStatus(scenario.seatIdsByCode.getValue("1A")))
            assertEquals("occupied", seatStatus(scenario.seatIdsByCode.getValue("1B")))
        }

    /**
     * Call the test-only route to attach the seeded booking session to this client.
     */
    private suspend fun HttpClient.setSeatBookingSession(
        scenario: SeededSeatSelectionScenario,
        adults: String = "1",
    ) {
        val response =
            get(
                "/__test/seat-session" +
                    "?bookingId=${scenario.bookingId}" +
                    "&flightId=${scenario.flightId}" +
                    "&fareId=${scenario.fareId}" +
                    "&adults=$adults",
            )

        assertEquals(HttpStatusCode.OK, response.status)
    }

    /**
     * Submit the same hidden selectedSeats field that the seat selection page posts.
     */
    private suspend fun HttpClient.submitSeats(selectedSeats: String) =
        submitForm(
            url = "/flights/seats",
            formParameters =
                parameters {
                    append("selectedSeats", selectedSeats)
                },
        )

    /**
     * Test-only route for putting a matching booking session into the authenticated client.
     */
    private fun ApplicationTestBuilder.installSeatSessionRoute() {
        application {
            routing {
                get("/__test/seat-session") {
                    val bookingId = call.request.queryParameters["bookingId"]?.toIntOrNull() ?: 0
                    val flightId = call.request.queryParameters["flightId"]?.toIntOrNull() ?: 0
                    val fareId = call.request.queryParameters["fareId"]?.toIntOrNull() ?: 0
                    val adults = call.request.queryParameters["adults"] ?: "1"

                    call.sessions.set(
                        BookingSession(
                            bookingId = bookingId,
                            outboundFlightId = flightId,
                            outboundFareId = fareId,
                            search =
                                FlightSearch(
                                    tripType = "oneway",
                                    origin = "LHR",
                                    destination = "DXB",
                                    departureDate = "2026-04-01",
                                    returnDate = null,
                                    adults = adults,
                                    children = "0",
                                    infants = "0",
                                ),
                        ),
                    )
                    call.respondText("ok")
                }
            }
        }
    }

    /**
     * Seed the minimum real DB rows needed by GET and POST /flights/seats.
     */
    private fun seedSeatSelectionScenario(
        passengerCount: Int = 1,
        occupiedSeatCodes: List<String> = emptyList(),
    ): SeededSeatSelectionScenario {
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val flightId =
            seedFlight(
                originAirportId = originAirportId,
                destinationAirportId = destinationAirportId,
                capacityValue = 6,
            )
        val fareClassId = seedFareClass()
        val fareId = seedFlightFare(flightId, fareClassId)
        val bookingId = seedBooking(userIdByEmail(), bookingReference = "SEATS$flightId")
        val passengerIds =
            (0 until passengerCount).map { index ->
                seedPassenger(
                    bookingId = bookingId,
                    firstName = passengerFirstNames[index],
                    lastName = "Passenger",
                )
            }
        val seatIdsByCode =
            listOf("1A", "1B", "1C", "1D", "1E", "1F")
                .associateWith { seatCode -> seedSeat(flightId, seatCode) }

        occupiedSeatCodes.forEach { seatCode ->
            updateSeatStatus(seatIdsByCode.getValue(seatCode), "occupied")
        }

        return SeededSeatSelectionScenario(
            bookingId = bookingId,
            flightId = flightId,
            fareId = fareId,
            passengerIds = passengerIds,
            seatIdsByCode = seatIdsByCode,
        )
    }

    /**
     * Mark a seeded seat as available or occupied for validation tests.
     */
    private fun updateSeatStatus(
        seatId: Int,
        status: String,
    ) {
        transaction {
            SeatTable.update({ SeatTable.id eq seatId }) {
                it[SeatTable.status] = status
            }
        }
    }

    /**
     * Count assignments across this booking's generated seat-selection segments.
     */
    private fun seatAssignmentCountForBooking(bookingId: Int): Int =
        transaction {
            val segmentIds =
                BookingSegmentTable
                    .select { BookingSegmentTable.bookingId eq bookingId }
                    .map { it[BookingSegmentTable.id] }

            segmentIds.sumOf { segmentId ->
                SeatAssignmentTable
                    .select { SeatAssignmentTable.bookingSegmentId eq segmentId }
                    .count()
                    .toInt()
            }
        }

    /**
     * Decode redirect locations so error messages can be asserted as normal text.
     */
    private fun assertRedirectError(
        location: String?,
        expectedError: String,
    ) {
        val decodedLocation = URLDecoder.decode(location.orEmpty(), StandardCharsets.UTF_8)

        assertTrue(decodedLocation.startsWith("/flights/seats?error="))
        assertTrue(decodedLocation.contains(expectedError))
    }

    /**
     * Keep the ids seeded for each test together so assertions can use exact rows.
     */
    private data class SeededSeatSelectionScenario(
        val bookingId: Int,
        val flightId: Int,
        val fareId: Int,
        val passengerIds: List<Int>,
        val seatIdsByCode: Map<String, Int>,
    )

    /**
     * List of names
     */
    private companion object {
        val passengerFirstNames = listOf("Alex", "Casey", "Jordan", "Taylor")
    }
}
