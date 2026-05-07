package com.flightbooking.access

import com.flightbooking.mappers.toSeat
import com.flightbooking.models.Seat
import com.flightbooking.tables.SeatTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Class instance for using seat table
 */
class SeatTableAccess {
    /**
     * Gets list of all seats
     * @return list of seats
     */
    fun getAll(): List<Seat> =
        transaction {
            SeatTable.selectAll().map {
                it.toSeat()
            }
        }

    /**
     * Gets list of seats from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of seats
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Seat> =
        transaction {
            SeatTable.select { attribute eq value }
                .map { it.toSeat() }
        }

    /**
     * Creates a seat record
     * @param seat seat model
     * @return true if created
     */
    fun createSeat(seat: Seat): Boolean =
        transaction {
            SeatTable.insert {
                it[SeatTable.flightId] = seat.flightId
                it[SeatTable.seatCode] = seat.seatCode
                it[SeatTable.cabinClass] = seat.cabinClass
                it[SeatTable.position] = seat.position
                it[SeatTable.extraLegroom] = seat.extraLegroom
                it[SeatTable.exitRow] = seat.exitRow
                it[SeatTable.reducedMobility] = seat.reducedMobility
                it[SeatTable.status] = seat.status
            }
            true
        }

    /**
     * Deletes a seat by searching with it's ID
     * @param id seat id
     */
    fun deleteByID(id: Int) =
        transaction {
            SeatTable.deleteWhere { SeatTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id seat id
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
                SeatTable.update(
                    { SeatTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
