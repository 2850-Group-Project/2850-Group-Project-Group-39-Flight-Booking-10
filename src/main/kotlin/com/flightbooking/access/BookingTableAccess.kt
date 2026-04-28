package com.flightbooking.access

import com.flightbooking.models.Booking
import com.flightbooking.mappers.toBooking
import com.flightbooking.tables.BookingTable

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import java.time.Instant
import com.flightbooking.tables.UserTable
import java.time.LocalDateTime

const val RAND_CANCELLED_UPPER : Int = 10
const val RAND_CANCELLED_AT_UPPER : Int = 300
const val RAND_BOOKING_MAX : Int = 2
const val RAND_LETTER_COUNT : Int = 3
const val RAND_NUMBER_MIN : Int = 100
const val RAND_NUMBER_MAX : Int = 999

class BookingTableAccess {
    fun getAll(): List<Booking> = transaction {
        BookingTable.selectAll().map {
            it.toBooking()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Booking> = transaction {
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
        amendable: Int
        ): Boolean = transaction { 
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
    fun deleteByID(id: Int) = transaction { 
        BookingTable.deleteWhere { BookingTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = BookingTable.update({ BookingTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    fun generateBookings() = transaction {
        println("generating bookings")
        val users = UserTable.selectAll().toList()
        users.forEach { row ->
            val userId = row[UserTable.id]
            val numberOfBookings = (1..RAND_BOOKING_MAX).random()
            repeat(numberOfBookings) {
                val bookingRef = generateBookingReference()
                val isCancelled = (1..RAND_CANCELLED_UPPER).random() == 1
                val cancelledAt = if (isCancelled) {
                    java.time.LocalDateTime.now()
                    .minusDays((1..RAND_CANCELLED_AT_UPPER).random().toLong())
                    .toString()
                } else null
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
