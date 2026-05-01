package com.flightbooking.access

import com.flightbooking.mappers.toAirport
import com.flightbooking.models.Airport
import com.flightbooking.tables.AirportTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private const val AIRPORT_SEARCH_LIMIT: Int = 8

/**
 * Class instance for using airport table
 */
class AirportTableAccess {

    /**
     * Gets list of all Airports
     * @return list of airports
     */
    fun getAll(): List<Airport> =
        transaction {
            AirportTable.selectAll().map {
                it.toAirport()
            }
        }

    /**
     * Gets list of Airport from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of airports
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Airport> =
        transaction {
            AirportTable.select { attribute eq value }
                .map { it.toAirport() }
        }

    /**
     * Gets iata code of airport from origin
     * @param origin search text
     * @return iata code or null
     */
    fun getAirportCodeByOrigin(origin: String): String? =
        transaction {
            AirportTable.select {
                (AirportTable.iataCode like "%$origin%") or
                    (AirportTable.city like "%$origin%") or
                    (AirportTable.name like "%$origin%")
            }.firstOrNull()?.get(AirportTable.iataCode)
        }

    /**
     * Gets city name of airport from origin
     * @param origin search text
     * @return city or null
     */
    fun getCityByOrigin(origin: String): String? =
        transaction {
            AirportTable.select {
                (AirportTable.iataCode like "%$origin%") or
                    (AirportTable.city like "%$origin%") or
                    (AirportTable.name like "%$origin%")
            }.firstOrNull()?.get(AirportTable.city)
        }

    /**
     * Searches table with query and returns list of Airports that are similar
     * @param query search text
     * @return list of airports
     */
    fun searchAirports(query: String): List<Airport> =
        transaction {
            val pattern = "%$query%"

            AirportTable.select {
                (AirportTable.iataCode like pattern) or
                    (AirportTable.city like pattern) or
                    (AirportTable.name like pattern)
            }
                .limit(AIRPORT_SEARCH_LIMIT)
                .map { it.toAirport() }
                .sortedBy { airport ->
                    when {
                        airport.iataCode.startsWith(query, ignoreCase = true) -> 0
                        airport.city?.startsWith(query, ignoreCase = true) == true -> 1
                        else -> 2
                    }
                }
        }

    /**
     * Creates an airport object
     * @param iataCode airport code
     * @param name airport name
     * @param city airport city
     * @param country airport country
     * @return true if created
     */
    fun createAirport(
        iataCode: String,
        name: String?,
        city: String?,
        country: String?,
    ): Boolean =
        transaction {
            // inserts new record into the table and returns the generated id
            AirportTable.insert {
                it[AirportTable.iataCode] = iataCode
                it[AirportTable.name] = name
                it[AirportTable.city] = city
                it[AirportTable.country] = country
            }
            true
        }

    /**
     * Deletes an Airport by searching with it's ID
     * @param id airport id
     */
    fun deleteByID(id: Int) =
        transaction {
            // deletes record by id
            AirportTable.deleteWhere { AirportTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id airport id
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
            // updates the record with given id, given column and value
            val rows =
                AirportTable.update({ AirportTable.id eq id }) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }

    /**
     * Updates Airport if it exists, otherwise inserts it
     * @param airport airport model
     */
    fun upsertByIata(airport: Airport) =
        transaction {
            // used for the db import
            val existing =
                AirportTable
                    .select { AirportTable.iataCode eq airport.iataCode }
                    .singleOrNull()

            if (existing == null) {
                AirportTable.insert {
                    it[iataCode] = airport.iataCode
                    it[name] = airport.name
                    it[AirportTable.city] = airport.city
                    it[AirportTable.country] = airport.country
                }
            } else {
                AirportTable.update({ AirportTable.iataCode eq airport.iataCode }) {
                    it[name] = airport.name
                    it[city] = airport.city
                    it[country] = airport.country
                }
            }
        }
}
