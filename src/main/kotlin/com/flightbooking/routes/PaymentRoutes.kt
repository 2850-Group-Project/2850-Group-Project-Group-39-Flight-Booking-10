package com.flightbooking.routes

import com.flightbooking.access.BookingTableAccess
import com.flightbooking.access.PaymentTableAccess
import com.flightbooking.models.BookingSession
import com.flightbooking.models.Payment
import com.flightbooking.models.UserSession
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
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

private const val RETURN_FARE_DISCOUNT = 0.5
private const val PROVIDER_REFERENCE_DIGITS = 4

fun Route.paymentRoutes() {
    get("/payment") { handleGetPayment(call) }
    post("/payment") { handlePostPayment(call) }
}

private suspend fun handlePostPayment(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    val bookingSession = call.sessions.get<BookingSession>()

    if (userSession == null) {
        call.respondRedirect("/login")
        return
    }

    if (bookingSession == null) {
        call.respondRedirect("/home")
        return
    }

    val params = call.receiveParameters()
    val cardNumber = params["cardNumber"]?.trim()
    val expiry = params["expiry"]?.trim()
    val cvv = params["cvv"]?.trim()
    val finalTotal = calculateTotal(bookingSession)
    println("Payment submitted: Card: $cardNumber, Expiry: $expiry, CVV: $cvv")

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
    val bookingTableAccess = BookingTableAccess()
    bookingTableAccess.createBookingWithPaymentUpdate(bookingSession, paymentId, userSession.userEmail)

    call.respondRedirect("/confirmation")
}

private suspend fun handleGetPayment(call: ApplicationCall) {
    val userSession = call.sessions.get<UserSession>()
    val bookingSession = call.sessions.get<BookingSession>()
    println("bookingSession = $bookingSession")
    println("bookingSession.search = ${bookingSession?.search}")

    if (userSession == null) {
        call.respondRedirect("/login")
        return
    }
    if (bookingSession == null) {
        call.respondRedirect("/home")
        return
    }

    call.respond(
        PebbleContent(
            "payment.peb",
            mapOf(
                "userSession" to userSession,
                "bookingSession" to bookingSession,
            ),
        ),
    )
}

private suspend fun calculateTotal(bookingSession: BookingSession): Double {
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

    return (outboundFarePrice + discountedReturnFare) * passengerCount
}
