package com.flightbooking.access

import com.flightbooking.models.BookingSegment
import com.flightbooking.models.toBookingSegment
import com.flightbooking.tables.BookingSegmentTable
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
import org.jetbrains.exposed.sql.insertAndGetId


import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FareClassTable

const val RAND_CABIN_UPPER : Int = 100
const val RAND_CABIN_BUSINESS_UPPER : Int = 6
const val RAND_CABIN_PREMIUM_ECONOMY_UPPER : Int = 20

class BookingSegmentTableAccess {
    fun getAll(): List<BookingSegment> = transaction {
        BookingSegmentTable.selectAll().map {
            it.toBookingSegment()
        }}
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<BookingSegment> = transaction {
        BookingSegmentTable.select { attribute eq value } 
            .map { it.toBookingSegment() } }
    fun createBookingSegment(
        bookingId: Int, 
        flightId: Int, 
        flightFareId: Int
        ): Boolean = transaction { 
        BookingSegmentTable.insert { 
            it[BookingSegmentTable.bookingId] = bookingId 
            it[BookingSegmentTable.flightId] = flightId 
            it[BookingSegmentTable.flightFareId] = flightFareId 
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        BookingSegmentTable.deleteWhere { BookingSegmentTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = BookingSegmentTable.update({ BookingSegmentTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    fun generateBookingSegments(
        activeFlights: List<Int>,
        passengersByBooking: Map<Int, List<Int>>
    ): Map<Int, List<Int>> = transaction {
        println("generating booking segments")
        val segmentsByBooking = mutableMapOf<Int, MutableList<Int>>()
        val bookings = BookingTable.selectAll().toList()
        val flightFares = FlightFareTable
            .select { FlightFareTable.flightId inList activeFlights }
            .toList()
        val fareClassIds = flightFares.map { it[FlightFareTable.fareClassId] }.distinct()
        val fareClasses = FareClassTable
            .select { FareClassTable.id inList fareClassIds }
            .associateBy { it[FareClassTable.id] }
        val faresByFlightAndCabin = flightFares.groupBy { it[FlightFareTable.flightId] }
            .mapValues { (_, fares) ->
                fares.groupBy { fareRow ->
                    val fc = fareClasses[fareRow[FlightFareTable.fareClassId]]
                    fc?.get(FareClassTable.cabinClass) ?: "Economy"
                }
            }
        fun pickCabin(): String {
            val roll = (1..RAND_CABIN_UPPER).random()
            return when {
                roll <= RAND_CABIN_BUSINESS_UPPER -> "Business"
                roll <= RAND_CABIN_PREMIUM_ECONOMY_UPPER -> "Premium Economy"
                else -> "Economy"
            }
        }
        bookings.forEach { bookingRow ->
            val bookingId = bookingRow[BookingTable.id]
            val passengers = passengersByBooking[bookingId] ?: emptyList()
            if (passengers.isEmpty()) return@forEach
            val flightId = activeFlights.random()
            val cabinMap = faresByFlightAndCabin[flightId] ?: emptyMap()
            val desiredCabin = pickCabin()
            val cabinFares = cabinMap[desiredCabin].orEmpty()
            val fareRow =
                if (cabinFares.isNotEmpty()) cabinFares.random()
                else cabinMap.values.flatten().random()
            segmentsByBooking[bookingId] = mutableListOf()
            for (ignored in passengers) {
                val segmentId = BookingSegmentTable.insert { row ->
                    row[BookingSegmentTable.bookingId] = bookingId
                    row[BookingSegmentTable.flightId] = flightId
                    row[BookingSegmentTable.flightFareId] = fareRow[FlightFareTable.id]
                }[BookingSegmentTable.id]
                segmentsByBooking[bookingId]!!.add(segmentId)
            }
        }
        println("done generating booking segments")
        segmentsByBooking
    }
}
