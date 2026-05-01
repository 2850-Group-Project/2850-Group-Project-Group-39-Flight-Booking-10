package com.flightbooking.routes

import com.flightbooking.access.BookingTableAccess
import com.flightbooking.access.PaymentTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Payment
import com.flightbooking.models.UserSession
import com.flightbooking.service.PointsService
import com.flightbooking.tables.FareClassTable
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

fun Route.paymentRoutes() {
    get("/payment") { handleGetPayment(call) }
    post("/payment") { handlePostPayment(call) }
}

private suspend fun handleGetPayment(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    val bookingSession = call.sessions.get<BookingSession>()
    val userId = userSession?.let { fetchUserId(it) }

    if (userSession == null || bookingSession == null || userId == null) {
        call.respondRedirect(if (bookingSession == null) "/home" else "/login")
        return
    }

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

private suspend fun handlePostPayment(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    val bookingSession = call.sessions.get<BookingSession>()
    val userId = userSession?.let { fetchUserId(it) }

    if (userSession == null || bookingSession == null || userId == null) {
        call.respondRedirect(if (bookingSession == null) "/home" else "/login")
        return
    }

    val params = call.receiveParameters()
    val cardNumber = params["cardNumber"]?.trim()
    val expiry = params["expiry"]?.trim()
    val cvv = params["cvv"]?.trim()
    var finalTotal = calculateTotal(bookingSession)
    println("Payment submitted: Card: $cardNumber, Expiry: $expiry, CVV: $cvv")

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
                bookingId = bookingSession.bookingId,
                amount = finalTotal,
                paymentMethod = "card",
                paymentStatus = "paid",
                paidAt = java.time.LocalDateTime.now().toString(),
                providerReference = cardNumber?.takeLast(PROVIDER_REFERENCE_DIGITS) ?: "0000",
                currency = "GBP",
            )

    val bookingTableAccess = BookingTableAccess()
    bookingTableAccess.createBookingWithPaymentUpdate(bookingSession, paymentId, userSession.userEmail)

    val milesEarnRate = fetchMilesEarnRate(bookingSession.outboundFareId)

    PointsService.awardPointsForBooking(
        userId = userId,
        bookingId = bookingSession.bookingId,
        amountPaid = finalTotal,
        milesEarnRate = milesEarnRate,
    )

    call.respondRedirect("/confirmation")
}

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

private fun fetchMilesEarnRate(outboundFareId: Int?): Double {
    if (outboundFareId == null) return 1.0
    return transaction {
        (FlightFareTable innerJoin FareClassTable)
            .select { FlightFareTable.id eq outboundFareId }
            .singleOrNull()
            ?.get(FareClassTable.milesEarnRate)
            ?: 1.0
    }
}
