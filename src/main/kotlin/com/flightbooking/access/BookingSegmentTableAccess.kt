package com.flightbooking.access

import com.flightbooking.mappers.toBookingSegment
import com.flightbooking.models.BookingSegment
import com.flightbooking.tables.BookingSegmentTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

const val RAND_CABIN_UPPER: Int = 100
const val RAND_CABIN_BUSINESS_UPPER: Int = 6
const val RAND_CABIN_PREMIUM_ECONOMY_UPPER: Int = 20

/**
 * Class instance for using BookingSegment table
 */
class BookingSegmentTableAccess {
    /**
     * Gets list of all BookingSegments
     */
    fun getAll(): List<BookingSegment> =
        transaction {
            BookingSegmentTable.selectAll().map {
                it.toBookingSegment()
            }
        }

    /**
     * Gets list of BookigSegment from DB, filtering by attribute and value you want it to be
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<BookingSegment> =
        transaction {
            BookingSegmentTable.select { attribute eq value }
                .map { it.toBookingSegment() }
        }

    /**
     * Creates an BookignSegment object
     */
    fun createBookingSegment(
        bookingId: Int,
        flightId: Int,
        flightFareId: Int,
    ): Boolean =
        transaction {
            BookingSegmentTable.insert {
                it[BookingSegmentTable.bookingId] = bookingId
                it[BookingSegmentTable.flightId] = flightId
                it[BookingSegmentTable.flightFareId] = flightFareId
            }
            true
        }

    /**
     * Deletes a BookingSegment by searching with it's ID
     */
    fun deleteByID(id: Int) =
        transaction {
            BookingSegmentTable.deleteWhere { BookingSegmentTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     */
    fun <T> updateRecordByAttribute(
        id: Int,
        column: Column<T>,
        value: T,
    ): Boolean =
        transaction {
            val rows =
                BookingSegmentTable.update(
                    { BookingSegmentTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
