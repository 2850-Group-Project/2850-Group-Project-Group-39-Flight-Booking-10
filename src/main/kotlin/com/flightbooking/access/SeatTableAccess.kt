package com.flightbooking.access

import com.flightbooking.models.Seat
import com.flightbooking.mappers.toSeat
import com.flightbooking.tables.SeatTable

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

import com.flightbooking.access.AirportTableAccess
import com.flightbooking.access.FlightTableAccess
import com.flightbooking.tables.FlightTable

const val DEFAULT_FLIGHT_CAPACITY : Int = 150
const val BUSINESS_ROWS_UPPER_LIMIT : Int = 5
const val PREMIUM_ROWS_UPPER_LIMIT : Int = 9
const val PREMIUM_ROWS_LOWER_LIMIT : Int = 6
const val ECONOMY_ROWS_DENOMINATOR : Float = 6.0f
const val EXIT_ROW_OFFSET_1 : Int = 4
const val EXIT_ROW_OFFSET_2 : Int = 5

class SeatTableAccess {
    fun getAll(): List<Seat> = transaction {
        SeatTable.selectAll().map {
            it.toSeat()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Seat> = transaction {
        SeatTable.select { attribute eq value } 
            .map { it.toSeat() } 
    }
    @Suppress("LongParameterList")
    fun createSeat(
        flightId: Int,
        seatCode: String,
        cabinClass: String?,
        position: String?,
        extraLegroom: Int,
        exitRow: Int,
        reducedMobility: Int,
        status: String
        ) : Boolean = transaction { 
        SeatTable.insert { 
            it[SeatTable.flightId] = flightId
            it[SeatTable.seatCode] = seatCode
            it[SeatTable.cabinClass] = cabinClass
            it[SeatTable.position] = position
            it[SeatTable.extraLegroom] = extraLegroom
            it[SeatTable.exitRow] = exitRow
            it[SeatTable.reducedMobility] = reducedMobility
            it[SeatTable.status] = status
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        SeatTable.deleteWhere { SeatTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = SeatTable.update(
            { SeatTable.id eq id }
            ) { stmt ->
            stmt[column] = value } 
        rows > 0 
    }
    fun generateUKDomesticSeats(activeFlights: List<Int>) = transaction {
        val ukDomesticFlights = FlightTable
            .select { FlightTable.id inList activeFlights }
            .toList()

        for (flight in ukDomesticFlights) {
            val flightId = flight[FlightTable.id]
            val capacity = flight[FlightTable.capacity] ?: DEFAULT_FLIGHT_CAPACITY
            generateBusinessSeats(flightId)
            generatePremiumSeats(flightId)
            generateEconomySeats(flightId, capacity)
        }
    }
    private val businessSeatLetters = listOf("A", "C", "D", "F")
    private val premiumSeatLetters  = listOf("A", "B", "C", "D", "E", "F")
    private val economySeatLetters  = listOf("A", "B", "C", "D", "E", "F")
    private val businessRows = 1..BUSINESS_ROWS_UPPER_LIMIT
    private val premiumRows  = PREMIUM_ROWS_LOWER_LIMIT..PREMIUM_ROWS_UPPER_LIMIT
    private fun generateBusinessSeats(flightId: Int) {
        for (row in businessRows) {
            for (letter in businessSeatLetters) {
                createSeat(
                    flightId = flightId,
                    seatCode = "$row$letter",
                    cabinClass = "Business",
                    position = when (letter) {
                        "A", "F" -> "window"
                        "C", "D" -> "aisle"
                        else -> null
                    },
                    extraLegroom = if (row == 1) 1 else 0,
                    exitRow = 0,
                    reducedMobility = if (row == 1 && (letter == "C" || letter == "D")) 1 else 0,
                    status = "available"
                )
            }
        }
    }
    private fun generatePremiumSeats(flightId: Int) {
        for (row in premiumRows) {
            for (letter in premiumSeatLetters) {
                createSeat(
                    flightId = flightId,
                    seatCode = "$row$letter",
                    cabinClass = "Premium Economy",
                    position = when (letter) {
                        "A", "F" -> "window"
                        "C", "D" -> "aisle"
                        else -> "middle"
                    },
                    extraLegroom = if (row == premiumRows.first) 1 else 0,
                    exitRow = 0,
                    reducedMobility = 0,
                    status = "available"
                )
            }
        }
    }
    private fun generateEconomySeats(flightId: Int, capacity: Int) {
        val businessSeats = businessRows.count() * businessSeatLetters.count()
        val premiumSeats  = premiumRows.count() * premiumSeatLetters.count()
        val remainingSeats = capacity - (businessSeats + premiumSeats)
        val economyRowsNeeded = kotlin.math.ceil(remainingSeats / ECONOMY_ROWS_DENOMINATOR).toInt()
        val economyStart = premiumRows.last + 1
        val economyRows = economyStart until (economyStart + economyRowsNeeded)
        for (row in economyRows) {
            val isExitRow = (row == economyRows.first + EXIT_ROW_OFFSET_1) || 
            (row == economyRows.first + EXIT_ROW_OFFSET_2)
            val extraLegroom = isExitRow || row == economyRows.first
            for (letter in economySeatLetters) {
                createSeat(
                    flightId = flightId,
                    seatCode = "$row$letter",
                    cabinClass = "Economy",
                    position = when (letter) {
                        "A", "F" -> "window"
                        "C", "D" -> "aisle"
                        else -> "middle"
                    },
                    extraLegroom = if (extraLegroom) 1 else 0,
                    exitRow = if (isExitRow) 1 else 0,
                    reducedMobility = 0,
                    status = "available"
                )
            }
        }
    }
}
