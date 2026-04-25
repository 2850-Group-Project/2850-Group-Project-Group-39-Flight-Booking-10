package com.flightbooking.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respond
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions

import com.flightbooking.models.UserSession
import com.flightbooking.models.BookingSession

import com.flightbooking.models.FlightSearch
import com.flightbooking.models.PassengerInput

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.insert

import org.jetbrains.exposed.sql.transactions.transaction
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.PaymentTable
import com.flightbooking.tables.BookingTable

private const val RETURN_FARE_DISCOUNT = 0.5
private const val PROVIDER_REFERENCE_DIGITS = 4

fun Route.paymentRoutes() {
    get("/payment") {
        val userSession = call.sessions.get<UserSession>()
        val bookingSession = call.sessions.get<BookingSession>()
        println("bookingSession = $bookingSession")
        println("bookingSession.search = ${bookingSession?.search}")

        if (userSession == null) {
            call.respondRedirect("/login")
            return@get
        }
        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@get
        }

        call.respond(
            PebbleContent(
                "payment.peb",
                mapOf(
                    "userSession" to userSession,
                    "bookingSession" to bookingSession
                )
            )
        )
    }

    post("/payment") {
        val userSession = call.sessions.get<UserSession>()
        val bookingSession = call.sessions.get<BookingSession>()

        if (userSession == null) {
            call.respondRedirect("/login")
            return@post
        }
        if (bookingSession == null) {
            call.respondRedirect("/home")
            return@post
        }

        val params = call.receiveParameters()
        val cardNumber = params["cardNumber"]?.trim()
        val expiry = params["expiry"]?.trim()
        val cvv = params["cvv"]?.trim()

        println("Payment submitted:")
        println("Card: $cardNumber, Expiry: $expiry, CVV: $cvv")

        val outboundFarePrice = bookingSession.outboundFareId?.let { fareId ->
            transaction {
                FlightFareTable
                    .select { FlightFareTable.id eq fareId }
                    .single()[FlightFareTable.price]
            }
        } ?: 0.0
        println("outboundFarePrice = $outboundFarePrice")

        val returnFarePrice = bookingSession.returnFareId?.let { fareId ->
            transaction {
                FlightFareTable
                    .select { FlightFareTable.id eq fareId }
                    .single()[FlightFareTable.price]
            }
        } ?: 0.0
        val discountedReturnFare = if (bookingSession.returnFareId != null) {
            returnFarePrice * RETURN_FARE_DISCOUNT
        } else {
            returnFarePrice
        }
        println("discountedReturnFare = $discountedReturnFare")

        val adults = bookingSession.search?.adults?.toIntOrNull() ?: 0
        val children = bookingSession.search?.children?.toIntOrNull() ?: 0
        val infants = bookingSession.search?.infants?.toIntOrNull() ?: 0
        val passengerCount = adults + children + infants
        println("passengerCount = $passengerCount")

        val baseTotal = outboundFarePrice + discountedReturnFare
        val finalTotal = baseTotal * passengerCount
        println("total = $finalTotal")

        val paymentId = transaction {
            PaymentTable.insert {
                it[bookingId] = bookingSession.bookingId
                it[amount] = finalTotal
                it[currency] = "GBP"
                it[paymentStatus] = "paid"
                it[paymentMethod] = "card"
                it[providerReference] = cardNumber?.takeLast(PROVIDER_REFERENCE_DIGITS) ?: "0000"
            }[PaymentTable.id]
        }

        transaction {
            BookingTable.update({ BookingTable.id eq bookingSession.bookingId }) {
                it[BookingTable.bookingStatus] = "pending_confirmation"
                it[BookingTable.paymentId] = paymentId
            }
        }

        call.respondRedirect("/confirmation")
    }
}
