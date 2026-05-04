package com.flightbooking.routes

import com.flightbooking.access.BookingTableAccess
import com.flightbooking.access.PaymentTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Payment
import com.flightbooking.service.AuthService
import com.flightbooking.service.PointsService
import com.flightbooking.tables.FlightFareTable
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Locale

private const val RETURN_FARE_DISCOUNT = 0.5
private const val PROVIDER_REFERENCE_DIGITS = 4

/**
 * Routes for payment page
 * Routes:
 *  - GET /payment -> renders and displays payment page
 *  - POST /payment -> accepts inputs for payment information,
 * Calculates the total and creates Payment to store into Payment table
 * Users membership points also factored into payment calculation
 */
fun Route.paymentRoutes() {
    get("/payment") { handleGetPayment(call) }
    post("/payment") { handlePostPayment(call) }
}

/**
 * Handler function for displaying payment page, displays points discount
 * @param call request call
 */
private suspend fun handleGetPayment(call: ApplicationCall) {
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (userSession, userId) = AuthService.requireUser(call) ?: return

    val bookingTotal = calculateTotal(bookingSession)
    val (pointsAvailable, maxDiscount) = PointsService.calculateRedemption(userId, bookingTotal)

    call.respond(
        PebbleContent(
            "payment.peb",
            mapOf(
                "userSession" to userSession,
                "bookingSession" to bookingSession,
                "pointsAvailable" to pointsAvailable,
                "maxDiscount" to String.format(Locale.UK, "%.2f", maxDiscount),
                "bookingTotal" to bookingTotal,
            ),
        ),
    )
}

/**
 * Accepts payment information from payment page, calculates final total including discount
 * Adds points to user accounts based on miles earn rate
 * Creates Payment and inserts it into the payment table
 * @param call request call
 */
private suspend fun handlePostPayment(call: ApplicationCall) {
    val bookingSession = AuthService.requireBooking(call) ?: return
    val (userSession, userId) = AuthService.requireUser(call) ?: return

    val params = call.receiveParameters()
    val cardNumber = params["cardNumber"]?.trim()
    val expiry = params["expiry"]?.trim()
    val cvv = params["cvv"]?.trim()
    var finalTotal = calculateTotal(bookingSession)

    println("Expiry: $expiry\nCVV: $cvv")

    val pointsToRedeem = params["pointsToRedeem"]?.toIntOrNull() ?: 0

    if (pointsToRedeem > 0) {
        val discount =
            PointsService.redeemPoints(
                userId = userId,
                bookingId = bookingSession.bookingId,
                pointsToRedeem = pointsToRedeem,
                bookingTotal = finalTotal,
            )
        finalTotal -= discount
    }

    val paymentTableAccess = PaymentTableAccess()
    val paymentId =
        paymentTableAccess
            .createPayment(
                Payment(
                    bookingId = bookingSession.bookingId,
                    amount = finalTotal,
                    paymentMethod = "card",
                    paymentStatus = "paid",
                    paidAt = java.time.LocalDateTime.now().toString(),
                    providerReference = cardNumber?.takeLast(PROVIDER_REFERENCE_DIGITS) ?: "0000",
                    currency = "GBP",
                ),
            )

    val updatedBookingSession = bookingSession.copy(totalPrice = finalTotal)
    call.sessions.set(updatedBookingSession)

    val bookingTableAccess = BookingTableAccess()
    bookingTableAccess.createBookingWithPaymentUpdate(bookingSession, paymentId, userSession.userEmail)

    val milesEarnRate = PointsService.fetchMilesEarnRate(bookingSession.outboundFareId)

    PointsService.awardPointsForBooking(
        userId = userId,
        bookingId = bookingSession.bookingId,
        amountPaid = finalTotal,
        fareEarnRate = milesEarnRate,
    )

    call.respondRedirect("/confirmation")
}

/**
 * Helper function to calculate total price, based on if it's a return flight, and number of passengers
 * @param bookingSession booking session
 * @return total price
 */
private fun calculateTotal(bookingSession: BookingSession): Double =
    transaction {
        val outboundFarePrice =
            bookingSession.outboundFareId?.let { fareId ->
                transaction {
                    FlightFareTable
                        .select { FlightFareTable.id eq fareId }
                        .single()[FlightFareTable.price]
                }
            } ?: 0.0

        val returnFarePrice =
            bookingSession.returnFareId?.let { fareId ->
                transaction {
                    FlightFareTable
                        .select { FlightFareTable.id eq fareId }
                        .single()[FlightFareTable.price]
                }
            } ?: 0.0

        val discountedReturnFare =
            if (bookingSession.returnFareId != null) {
                returnFarePrice * RETURN_FARE_DISCOUNT
            } else {
                returnFarePrice
            }
        val adults = bookingSession.search?.adults?.toIntOrNull() ?: 0
        val children = bookingSession.search?.children?.toIntOrNull() ?: 0
        val infants = bookingSession.search?.infants?.toIntOrNull() ?: 0
        val passengerCount = adults + children + infants

        (outboundFarePrice + discountedReturnFare) * passengerCount
    }
