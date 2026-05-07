package com.flightbooking

import com.flightbooking.models.BookingSession
import com.flightbooking.models.FlightSearch
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
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfirmationRoutesTest : IntegrationTestSupport() {
    /**
     * Unauthenticated users should be redirected to login from confirmation.
     */
    @Test
    fun unauthenticatedConfirmationRedirectsToLogin() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login", response.headers[HttpHeaders.Location])
        }

    /**
     * Users without a booking session should be redirected home from confirmation.
     */
    @Test
    fun confirmationRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    /**
     * Confirmation should render the one-way booking session summary.
     */
    @Test
    fun confirmationPageShowsOneWayBookingSummary() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Booking Confirmed"))
            assertTrue(body.contains("Trip Type"))
            assertTrue(body.contains("One Way"))
            assertTrue(body.contains("1 Adult"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
            assertTrue(body.contains("Outbound Flight"))
            assertTrue(body.contains("Ticket Price"))
            assertTrue(body.contains("Loyalty Points Earned"))
        }

    /**
     * Confirmation should include return details when the booking session has a return leg.
     */
    @Test
    fun confirmationPageShowsReturnBookingSummary() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.selectReturnTrip()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Booking Confirmed"))
            assertTrue(body.contains("Return"))
            assertTrue(body.contains("Return Flight"))
            assertTrue(body.contains("Return Date"))
            assertTrue(body.contains("2026-04-10"))
            assertTrue(body.contains("30"))
        }

    /**
     * Confirmation should show points earned from a known paid booking total.
     */
    @Test
    fun confirmationPageShowsPointsEarnedForKnownTotalPrice() =
        testApplication {
            configureApp()
            installTestConfirmationSessionRoute()
            val client = createAuthenticatedUserClient()

            val sessionResponse = client.get("/__test/confirmation-session?totalPrice=200.0")
            assertEquals(HttpStatusCode.OK, sessionResponse.status)

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("£200.0"))
            assertTrue(body.contains("Loyalty Points Earned"))
            assertTrue(body.contains("100"))
        }

    /**
     * Confirmation should include child and infant passenger counts.
     */
    @Test
    fun confirmationPageShowsChildrenAndInfantsPassengerCounts() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.selectOneWayTrip(
                flightId = 10,
                fareId = 20,
                adults = "2",
                children = "1",
                infants = "1",
            )

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("2 Adults"))
            assertTrue(body.contains("1 Child"))
            assertTrue(body.contains("1 Infant"))
        }

    /**
     * Confirmation should render the exact selected outbound and return ids that are shown on the page.
     */
    @Test
    fun confirmationPageShowsExactSelectedFlightAndFareIds() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.selectReturnTrip(
                outboundFlightId = 1010,
                outboundFareId = 2020,
                returnFlightId = 3030,
                returnFareId = 4040,
            )

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("1010"))
            assertTrue(body.contains("2020"))
            assertTrue(body.contains("3030"))
        }

    /**
     * Confirmation should still render safely when no payment total has been set.
     */
    @Test
    fun confirmationPageRendersSafelyWithZeroTotal() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.seedBookingSession()

            val response = client.get("/confirmation")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Booking Confirmed"))
            assertTrue(body.contains("£0.0"))
            assertTrue(body.contains("Loyalty Points Earned"))
            assertTrue(body.contains("0"))
        }

    /**
     * Submits form for post /flights/select
     * @param flightId
     * @param fareId
     * @param adults
     * @param children
     * @param infants
     */
    private suspend fun HttpClient.selectOneWayTrip(
        flightId: Int,
        fareId: Int,
        adults: String = "1",
        children: String = "0",
        infants: String = "0",
    ) {
        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", flightId.toString())
                    append("fareId", fareId.toString())
                    append("leg", "outbound")
                    append("tripType", "oneway")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("adults", adults)
                    append("children", children)
                    append("infants", infants)
                },
        )
    }

    /**
     * Test only route to create fake booking session
     */
    private fun io.ktor.server.testing.ApplicationTestBuilder.installTestConfirmationSessionRoute() {
        application {
            routing {
                get("/__test/confirmation-session") {
                    val totalPrice = call.request.queryParameters["totalPrice"]?.toDoubleOrNull() ?: 0.0
                    call.sessions.set(
                        BookingSession(
                            bookingId = 123,
                            outboundFlightId = 10,
                            outboundFareId = null,
                            search =
                                FlightSearch(
                                    tripType = "oneway",
                                    origin = "LHR",
                                    destination = "DXB",
                                    departureDate = "2026-04-01",
                                    returnDate = null,
                                    adults = "1",
                                    children = "0",
                                    infants = "0",
                                ),
                            totalPrice = totalPrice,
                        ),
                    )
                    call.respondText("ok")
                }
            }
        }
    }

    /**
     * Helper function to choose the flight ids
     */
    private suspend fun io.ktor.client.HttpClient.selectReturnTrip() {
        selectReturnTrip(
            outboundFlightId = 10,
            outboundFareId = 20,
            returnFlightId = 30,
            returnFareId = 40,
        )
    }

    /**
     * Submits two forms for flights/select, outbound and return
     * @param outboundFlightId
     * @param outboundFareId
     * @param returnFlightId
     * @param returnFareId
     */
    private suspend fun HttpClient.selectReturnTrip(
        outboundFlightId: Int,
        outboundFareId: Int,
        returnFlightId: Int,
        returnFareId: Int,
    ) {
        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", outboundFlightId.toString())
                    append("fareId", outboundFareId.toString())
                    append("leg", "outbound")
                    append("tripType", "return")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("returnDate", "2026-04-10")
                    append("adults", "1")
                },
        )

        submitForm(
            url = "/flights/select",
            formParameters =
                parameters {
                    append("flightId", returnFlightId.toString())
                    append("fareId", returnFareId.toString())
                    append("leg", "return")
                    append("tripType", "return")
                    append("origin", "LHR")
                    append("destination", "DXB")
                    append("departureDate", "2026-04-01")
                    append("returnDate", "2026-04-10")
                    append("adults", "1")
                },
        )
    }
}
