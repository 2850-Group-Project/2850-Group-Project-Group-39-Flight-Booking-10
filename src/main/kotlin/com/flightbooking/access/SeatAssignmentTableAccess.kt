package com.flightbooking.access

import com.flightbooking.models.SeatAssignment
import com.flightbooking.models.toSeatAssignment
import com.flightbooking.tables.SeatAssignmentTable

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

import com.flightbooking.tables.PassengerTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.FlightFareTable
import com.flightbooking.tables.FlightTable
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.SeatTable
import org.jetbrains.exposed.sql.and
class SeatAssignmentTableAccess {
    fun getAll(): List<SeatAssignment> = transaction {
        SeatAssignmentTable.selectAll().map {
            it.toSeatAssignment()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<SeatAssignment> = transaction {
        SeatAssignmentTable.select { attribute eq value } 
            .map { it.toSeatAssignment() } 
    }
    fun createSeatAssignment(
        passengerId: Int,
        bookingSegmentId: Int,
        seatId: Int?
    ): Boolean = transaction {
        SeatAssignmentTable.insert {
            it[SeatAssignmentTable.passengerId] = passengerId
            it[SeatAssignmentTable.bookingSegmentId] = bookingSegmentId
            it[SeatAssignmentTable.seatId] = seatId
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        SeatAssignmentTable.deleteWhere { SeatAssignmentTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = SeatAssignmentTable.update({ SeatAssignmentTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    fun generateSeatAssignments(
        passengersByBooking: Map<Int, List<Int>>,
        segmentsByBooking: Map<Int, List<Int>>
    ) = transaction {
        println("generating seat assignments")
        passengersByBooking.forEach { (bookingId, passengers) ->
            val segments = segmentsByBooking[bookingId] ?: emptyList()
            val segmentId = segments.firstOrNull() ?: return@forEach

            for (passengerId in passengers) {
                SeatAssignmentTable.insert {
                    it[SeatAssignmentTable.passengerId] = passengerId
                    it[SeatAssignmentTable.bookingSegmentId] = segmentId
                    it[SeatAssignmentTable.seatId] = null
                }
            }
        }
        println("done generating seat assignments")
    }
    fun assignSeats() = transaction {
        println("assigning seats")
        val assignments = SeatAssignmentTable.selectAll().toList()
        assignments.forEach { assignment ->
            val assignmentId = assignment[SeatAssignmentTable.id]
            val segmentId = assignment[SeatAssignmentTable.bookingSegmentId]
            val segment = BookingSegmentTable
                .select { BookingSegmentTable.id eq segmentId }
                .first()
            val flightId = segment[BookingSegmentTable.flightId]
            val seat = SeatTable
                .select { SeatTable.flightId eq flightId }
                .firstOrNull()
            if (seat != null) {
                SeatAssignmentTable.update({ SeatAssignmentTable.id eq assignmentId }) {
                    it[SeatAssignmentTable.seatId] = seat[SeatTable.id]
                }
            }
        }
        println("done assigning seats")
    }
}
