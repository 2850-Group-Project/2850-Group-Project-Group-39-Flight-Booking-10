package com.flightbooking.access

import com.flightbooking.mappers.toBooking
import com.flightbooking.models.Booking
import com.flightbooking.models.BookingSession
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.PassengerTable
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
import java.util.UUID

private const val BOOKING_REFERENCE_LENGTH = 8

/**
 * Class instance for using Booking table
 */
class BookingTableAccess {
    /**
     * Gets list of all Bookings
     * @return list of bookings
     */
    fun getAll(): List<Booking> =
        transaction {
            BookingTable.selectAll().map {
                it.toBooking()
            }
        }

    /**
     * Gets list of Bookings from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of bookings
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Booking> =
        transaction {
            BookingTable.select { attribute eq value }
                .map { it.toBooking() }
        }

    /**
     * Creates a booking object
     * @param booking booking model
     * @return true if created
     */
    fun createBooking(booking: Booking): Boolean =
        transaction {
            BookingTable.insert {
                it[BookingTable.userId] = booking.userId
                it[BookingTable.bookingReference] = booking.bookingReference
                it[BookingTable.paymentId] = booking.paymentId
                it[BookingTable.createdAt] = Instant.now().toString()
                it[BookingTable.bookingStatus] = booking.bookingStatus
                it[BookingTable.cancelledAt] = booking.cancelledAt
                it[BookingTable.amendable] = booking.amendable
            }
            true
        }

    /**
     * Creates a booking object with payment id
     * @param bookingSession booking session
     * @param paymentId payment id
     * @param userEmail user email
     */
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

    /**
     * Deletes a booking by searching with it's ID
     * @param id booking id
     */
    fun deleteByID(id: Int) =
        transaction {
            BookingTable.deleteWhere { BookingTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id booking id
     * @param column column to update
     * @param value new value
     * @return true if updated
     */
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

    /**
     * Searches passenger table for bookingId
     * @param bookingId
     */
    fun getPassengersForBooking(bookingId: Int): List<Map<String, Any>> =
        transaction {
            PassengerTable
                .select { PassengerTable.bookingId eq bookingId }
                .map { row ->
                    mapOf<String, Any>(
                        "id" to row[PassengerTable.id],
                        "firstName" to (row[PassengerTable.firstName] ?: ""),
                        "lastName" to (row[PassengerTable.lastName] ?: ""),
                    )
                }
        }
}
