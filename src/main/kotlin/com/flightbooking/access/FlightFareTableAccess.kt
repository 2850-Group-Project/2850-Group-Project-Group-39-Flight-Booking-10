package com.flightbooking.access

import com.flightbooking.models.FlightFare
import com.flightbooking.models.toFlightFare
import com.flightbooking.tables.FlightFareTable

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
import com.flightbooking.access.FareClassTableAccess

import com.flightbooking.constants.FARE_CLASS_ECONOMY_ID
import com.flightbooking.constants.FARE_CLASS_ECONOMY_MULTIPLIER
import com.flightbooking.constants.FARE_CLASS_ECONOMY_PLUS_ID
import com.flightbooking.constants.FARE_CLASS_ECONOMY_PLUS_MULTIPLIER
import com.flightbooking.constants.FARE_CLASS_BUSINESS_ID
import com.flightbooking.constants.FARE_CLASS_BUSINESS_MULTIPLIER
import com.flightbooking.constants.FARE_CLASS_PREMIUM_ECONOMY_ID
import com.flightbooking.constants.FARE_CLASS_PREMIUM_ECONOMY_MULTIPLIER
import com.flightbooking.constants.FARE_CLASS_FIRST_ID
import com.flightbooking.constants.FARE_CLASS_FIRST_MULTIPLIER
import com.flightbooking.constants.BASE_PRICE_OFFSET
import com.flightbooking.constants.BASE_PRICE_FLIGHT_MOD
import com.flightbooking.constants.BASE_PRICE_MULTIPLIER
import com.flightbooking.constants.DEFAULT_CAPACITY
import com.flightbooking.constants.SEATS_DIVIDER_OFFSET
import com.flightbooking.constants.MIN_SEATS_AVAILABLE

class FlightFareTableAccess {
    fun getAll(): List<FlightFare> = transaction {
        FlightFareTable.selectAll().map {
            it.toFlightFare()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<FlightFare> = transaction {
        FlightFareTable.select { attribute eq value } 
            .map { it.toFlightFare() } 
    }
    
    @Suppress("LongParameterList")
    fun createFlightFare(
        flightId: Int, 
        fareClassId: Int, 
        price: Double, 
        currency: String, 
        seatsAvailable: Int, 
        saleStart: String?, 
        saleEnd: String?
        ): Boolean = transaction { 
        FlightFareTable.insert { 
            it[FlightFareTable.flightId] = flightId 
            it[FlightFareTable.fareClassId] = fareClassId 
            it[FlightFareTable.price] = price 
            it[FlightFareTable.currency] = currency 
            it[FlightFareTable.seatsAvailable] = seatsAvailable 
            it[FlightFareTable.saleStart] = saleStart 
            it[FlightFareTable.saleEnd] = saleEnd 
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        FlightFareTable.deleteWhere { FlightFareTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = FlightFareTable.update({ FlightFareTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    
    fun generateUKDomesticFares(
        airportAccess: AirportTableAccess = AirportTableAccess(),
        flightAccess: FlightTableAccess = FlightTableAccess(),
        fareClassAccess: FareClassTableAccess = FareClassTableAccess()
    ) = transaction {
        println("generating UK fares")
        val ukAirports = airportAccess.getByAttribute(com.flightbooking.tables.AirportTable.country,"United Kingdom")
        val ukIDs = ukAirports.map { it.id }
        val ukDomesticFlights = flightAccess.getDomesticUKFlights(ukIDs)

        val fareClasses = fareClassAccess.getAll()

        for (flight in ukDomesticFlights) {
            for (fareClass in fareClasses) {
                val existing = getByAttribute(
                    com.flightbooking.tables.FlightFareTable.flightId, flight.id
                ).any { it.fareClassId == fareClass.id }
                
                if (existing) continue

                val multiplier = when (fareClass.id) {
                    FARE_CLASS_ECONOMY_ID -> FARE_CLASS_ECONOMY_MULTIPLIER
                    FARE_CLASS_ECONOMY_PLUS_ID -> FARE_CLASS_ECONOMY_PLUS_MULTIPLIER
                    FARE_CLASS_BUSINESS_ID -> FARE_CLASS_BUSINESS_MULTIPLIER
                    FARE_CLASS_PREMIUM_ECONOMY_ID -> FARE_CLASS_PREMIUM_ECONOMY_MULTIPLIER
                    FARE_CLASS_FIRST_ID -> FARE_CLASS_FIRST_MULTIPLIER
                    else -> FARE_CLASS_ECONOMY_MULTIPLIER
                }

                val basePrice = BASE_PRICE_OFFSET + (flight.id % BASE_PRICE_FLIGHT_MOD) * BASE_PRICE_MULTIPLIER
                val capacity = flight.capacity ?: DEFAULT_CAPACITY
                val seatsAvailable = (capacity / (fareClass.id + SEATS_DIVIDER_OFFSET))
                    .coerceAtLeast(MIN_SEATS_AVAILABLE)

                createFlightFare(
                    flightId = flight.id,
                    fareClassId = fareClass.id,
                    price = basePrice * multiplier,
                    currency = "GBP",
                    seatsAvailable = seatsAvailable,
                    saleStart = null,
                    saleEnd = null
                )
            }
        }
        println("generated")
    }
}
