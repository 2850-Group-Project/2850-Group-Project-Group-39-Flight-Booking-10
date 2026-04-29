package com.flightbooking.access

import com.flightbooking.mappers.toBooking
import com.flightbooking.models.Booking
import com.flightbooking.models.BookingSession
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

private const val RAND_CANCELLED_UPPER: Int = 10
private const val RAND_CANCELLED_AT_UPPER: Int = 300
private const val RAND_BOOKING_MAX: Int = 2
private const val RAND_LETTER_COUNT: Int = 3
private const val RAND_NUMBER_MIN: Int = 100
private const val RAND_NUMBER_MAX: Int = 999
private const val BOOKING_REFERENCE_LENGTH = 8

class BookingTableAccess {
    fun getAll(): List<Booking> =
        transaction {
            BookingTable.selectAll().map {
                it.toBooking()
            }
        }

    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Booking> =
        transaction {
            BookingTable.select { attribute eq value }
                .map { it.toBooking() }
        }

    @Suppress("LongParameterList")
    fun createBooking(
        userId: Int?,
        bookingReference: String,
        paymentId: Int?,
        bookingStatus: String,
        cancelledAt: String?,
        amendable: Int,
    ): Boolean =
        transaction {
            BookingTable.insert {
                it[BookingTable.userId] = userId
                it[BookingTable.bookingReference] = bookingReference
                it[BookingTable.paymentId] = paymentId
                it[BookingTable.createdAt] = java.time.Instant.now().toString()
                it[BookingTable.bookingStatus] = bookingStatus
                it[BookingTable.cancelledAt] = cancelledAt
                it[BookingTable.amendable] = amendable
            }
            true
        }

    fun createBookingWithPaymentUpdate(
        bookingSession: BookingSession,
        paymentId: Int,
        userEmail: String,
    ) {
        transaction {
            val userId =
                UserTable
                    .select { UserTable.email eq userEmail }
                    .singleOrNull()
                    ?.get(UserTable.id)

            BookingTable.insert {
                it[BookingTable.id] = bookingSession.bookingId
                it[BookingTable.userId] = userId
                it[BookingTable.bookingReference] = UUID.randomUUID().toString().take(BOOKING_REFERENCE_LENGTH)
                it[BookingTable.bookingStatus] = "confirmed"
                it[BookingTable.amendable] = 1
            }

            BookingTable.update({ BookingTable.id eq bookingSession.bookingId }) {
                it[BookingTable.paymentId] = paymentId
            }
        }
    }

    fun deleteByID(id: Int) =
        transaction {
            BookingTable.deleteWhere { BookingTable.id eq id }
        }

    fun <T> updateRecordByAttribute(
        id: Int,
        column: Column<T>,
        value: T,
    ): Boolean =
        transaction {
            val rows =
                BookingTable.update(
                    { BookingTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }

    fun generateBookings() =
        transaction {
            println("generating bookings")
            val users = UserTable.selectAll().toList()
            users.forEach { row ->
                val userId = row[UserTable.id]
                val numberOfBookings = (1..RAND_BOOKING_MAX).random()
                repeat(numberOfBookings) {
                    val bookingRef = generateBookingReference()
                    val isCancelled = (1..RAND_CANCELLED_UPPER).random() == 1
                    val cancelledAt =
                        if (isCancelled) {
                            java.time.LocalDateTime.now()
                                .minusDays((1..RAND_CANCELLED_AT_UPPER).random().toLong())
                                .toString()
                        } else {
                            null
                        }
                    BookingTable.insert {
                        it[BookingTable.userId] = userId
                        it[BookingTable.bookingReference] = bookingRef
                        it[BookingTable.paymentId] = null
                        it[BookingTable.bookingStatus] = if (isCancelled) "cancelled" else "confirmed"
                        it[BookingTable.cancelledAt] = cancelledAt
                        it[BookingTable.amendable] = if (isCancelled) 0 else 1
                    }
                }
            }
            println("done generating")
        }

    fun generateBookingReference(): String {
        val letters = ('A'..'Z').shuffled().take(RAND_LETTER_COUNT).joinToString("")
        val numbers = (RAND_NUMBER_MIN..RAND_NUMBER_MAX).random()
        return "$letters$numbers"
    }
}
