package com.flightbooking.access

import com.flightbooking.mappers.toPassenger
import com.flightbooking.models.Passenger
import com.flightbooking.tables.PassengerTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Class instance for using passengers table
 */
class PassengerTableAccess {

    /**
     * Gets list of all passengers
     * @return list of passengers
     */
    fun getAll(): List<Passenger> =
        transaction {
            PassengerTable.selectAll().map {
                it.toPassenger()
            }
        }

    /**
     * Gets list of passengers from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of passengers
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Passenger> =
        transaction {
            PassengerTable.select { attribute eq value }
                .map { it.toPassenger() }
        }

    /**
     * Creates a passenger
     * @param passenger passenger model
     * @return true if created
     */
    fun createPassenger(passenger: Passenger): Boolean =
        transaction {
            PassengerTable.insert {
                it[PassengerTable.bookingId] = passenger.bookingId
                it[PassengerTable.email] = passenger.email
                it[PassengerTable.checkedIn] = passenger.checkedIn
                it[PassengerTable.title] = passenger.title
                it[PassengerTable.firstName] = passenger.firstName
                it[PassengerTable.lastName] = passenger.lastName
                it[PassengerTable.dateOfBirth] = passenger.dateOfBirth
                it[PassengerTable.gender] = passenger.gender
                it[PassengerTable.nationality] = passenger.nationality
                it[PassengerTable.documentType] = passenger.documentType
                it[PassengerTable.documentNumber] = passenger.documentNumber
                it[PassengerTable.documentCountry] = passenger.documentCountry
                it[PassengerTable.documentExpiry] = passenger.documentExpiry
            }
            true
        }

    /**
     * Deletes a passenger by searching with it's ID
     * @param id passenger id
     */
    fun deleteByID(id: Int) =
        transaction {
            PassengerTable.deleteWhere { PassengerTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id passenger id
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
                PassengerTable.update(
                    { PassengerTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
