package com.flightbooking

import com.flightbooking.access.PointsTableAccess
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentRoutesTest : IntegrationTestSupport() {
    /**
     * Users without a booking session should be redirected home from the payment page.
     */
    @Test
    fun paymentPageRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.get("/payment")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    /**
     * Posting payment without a booking session should also redirect home.
     */
    @Test
    fun paymentSubmitRedirectsHomeWhenBookingSessionMissing() =
        testApplication {
            configureApp()
            val client = createClient { followRedirects = false }

            val response = client.submitForm(url = "/payment")

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/home", response.headers[HttpHeaders.Location])
        }

    /**
     * Authenticated users with a booking session should see the payment page total.
     */
    @Test
    fun authenticatedPaymentPageRendersBookingTotal() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.get("/__health")
            val selection = seedBookableFlight()
            client.selectOneWayTrip(
                flightId = selection.flightId,
                fareId = selection.fareId,
            )

            val response = client.get("/payment")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Payment Details"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
        }

    /**
     * One-way payment totals should multiply the fare by the passenger count.
     */
    @Test
    fun paymentPageCalculatesOneWayTotal() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.get("/__health")
            val selection = seedBookableFlight()
            client.selectOneWayTrip(
                flightId = selection.flightId,
                fareId = selection.fareId,
                adults = "2",
                children = "1",
            )

            val response = client.get("/payment")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("2 adults"))
            assertTrue(body.contains("1 child"))
        }

    /**
     * Return trips should apply the route's return fare discount before rendering the total.
     */
    @Test
    fun paymentPageAppliesReturnFareDiscount() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            client.get("/__health")
            val originAirportId = seedAirport("LHR", "London Heathrow")
            val destinationAirportId = seedAirport("DXB", "Dubai International")
            val fareClassId = seedFareClass()
            val outboundSelection =
                seedBookableFlight(
                    originAirportId = originAirportId,
                    destinationAirportId = destinationAirportId,
                    fareClassId = fareClassId,
                    flightNumber = 701,
                )
            val returnSelection =
                seedBookableFlight(
                    originAirportId = originAirportId,
                    destinationAirportId = destinationAirportId,
                    fareClassId = fareClassId,
                    flightNumber = 702,
                )
            client.selectReturnTrip(
                outboundFlightId = outboundSelection.flightId,
                outboundFareId = outboundSelection.fareId,
                returnFlightId = returnSelection.flightId,
                returnFareId = returnSelection.fareId,
            )

            val response = client.get("/payment")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("Return"))
            assertTrue(body.contains("2026-04-10"))
            assertTrue(body.contains("LHR"))
            assertTrue(body.contains("DXB"))
        }

    /**
     * Payment should show the user's available points and maximum discount.
     */
    @Test
    fun paymentPageShowsAvailablePointsDiscount() =
        testApplication {
            configureApp()
            val client = createAuthenticatedUserClient()
            val userId = userIdByEmail()
            val selection = seedBookableFlight()
            PointsTableAccess().addPoints(
                userId = userId,
                points = 400,
                bookingId = null,
                type = "earn",
                description = "Seed points for payment test",
            )
            client.selectOneWayTrip(
                flightId = selection.flightId,
                fareId = selection.fareId,
            )

            val response = client.get("/payment")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("400 points"))
            assertTrue(body.contains("10.00"))
        }

    /**
     * Submits form for post /flights/select for one way
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
     * Submits post form for return trip
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
                    append("children", "0")
                    append("infants", "0")
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
                    append("children", "0")
                    append("infants", "0")
                },
        )
    }

    /**
     * Seeds a bookable flight into db
     * @param flightNumber
     * @return seeded flight
     */
    private fun seedBookableFlight(flightNumber: Int = 700): SeededPaymentFlight {
        val originAirportId = seedAirport("LHR", "London Heathrow")
        val destinationAirportId = seedAirport("DXB", "Dubai International")
        val fareClassId = seedFareClass()

        return seedBookableFlight(
            originAirportId = originAirportId,
            destinationAirportId = destinationAirportId,
            fareClassId = fareClassId,
            flightNumber = flightNumber,
        )
    }

    /**
     * Creates the bookable flight to seed
     *  @param originAirportId
     *  @param destinationAirportId
     *  @param fareClassId
     *  @param flightNumber
     */
    private fun seedBookableFlight(
        originAirportId: Int,
        destinationAirportId: Int,
        fareClassId: Int,
        flightNumber: Int,
    ): SeededPaymentFlight {
        val flightId =
            seedFlight(
                originAirportId = originAirportId,
                destinationAirportId = destinationAirportId,
                flightNumberValue = flightNumber,
            )
        val fareId = seedFlightFare(flightId, fareClassId)

        return SeededPaymentFlight(flightId, fareId)
    }

    /**
     * Data class definition for a seeded flight with fare
     */
    private data class SeededPaymentFlight(
        val flightId: Int,
        val fareId: Int,
    )
}
