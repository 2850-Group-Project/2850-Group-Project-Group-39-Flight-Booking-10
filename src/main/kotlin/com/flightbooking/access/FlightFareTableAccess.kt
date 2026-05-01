package com.flightbooking.access

import com.flightbooking.mappers.toFlightFare
import com.flightbooking.models.FlightFare
import com.flightbooking.tables.FlightFareTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Class instance for using flight fare table
 */
class FlightFareTableAccess {
    /**
     * Gets list of all flight fares
     * @return list of flight fares
     */
    fun getAll(): List<FlightFare> =
        transaction {
            FlightFareTable.selectAll().map {
                it.toFlightFare()
            }
        }

    /**
     * Gets list of flight fares from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of flight fares
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<FlightFare> =
        transaction {
            FlightFareTable.select { attribute eq value }
                .map { it.toFlightFare() }
        }

    /**
     * Creates a flight fare object
     * @param flightFare flight fare model
     * @return true if created
     */
    fun createFlightFare(flightFare: FlightFare): Boolean =
        transaction {
            FlightFareTable.insert {
                it[FlightFareTable.flightId] = flightFare.flightId
                it[FlightFareTable.fareClassId] = flightFare.fareClassId
                it[FlightFareTable.price] = flightFare.price
                it[FlightFareTable.currency] = flightFare.currency
                it[FlightFareTable.seatsAvailable] = flightFare.seatsAvailable
                it[FlightFareTable.saleStart] = flightFare.saleStart
                it[FlightFareTable.saleEnd] = flightFare.saleEnd
            }
            true
        }

    /**
     * Deletes a flight fare by searching with it's ID
     * @param id flight fare id
     */
    fun deleteByID(id: Int) =
        transaction {
            FlightFareTable.deleteWhere { FlightFareTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id flight fare id
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
                FlightFareTable.update(
                    { FlightFareTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
