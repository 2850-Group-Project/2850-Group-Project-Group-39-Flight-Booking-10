package com.flightbooking.access

import com.flightbooking.mappers.toFareClass
import com.flightbooking.models.FareClass
import com.flightbooking.tables.FareClassTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Class instance for using FareClass table
 */
class FareClassTableAccess {

    /**
     * Gets list of all fare classes
     * @return list of fare classes
     */
    fun getAll(): List<FareClass> =
        transaction {
            FareClassTable.selectAll().map {
                it.toFareClass()
            }
        }

    /**
     * Gets list of fare classes from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of fare classes
     */
    fun <T : Any> getByAttribute(
        attribute: Column<T?>,
        value: T?,
    ): List<FareClass> =
        transaction {
            val condition =
                if (value == null) {
                    attribute.isNull()
                } else {
                    attribute eq value
                }

            FareClassTable.select { condition }
                .map { it.toFareClass() }
        }

    /**
     * Creates a fare class object
     * @param fareClass fare class model
     * @return true if created
     */
    fun createFareClass(fareClass: FareClass): Boolean =
        transaction {
            FareClassTable.insert {
                it[FareClassTable.classCode] = fareClass.classCode
                it[FareClassTable.cabinClass] = fareClass.cabinClass
                it[FareClassTable.displayName] = fareClass.displayName
                it[FareClassTable.refundable] = fareClass.refundable
                it[FareClassTable.cancelProtocol] = fareClass.cancelProtocol
                it[FareClassTable.advanceSeatSelection] = fareClass.advanceSeatSelection
                it[FareClassTable.priorityCheckin] = fareClass.priorityCheckin
                it[FareClassTable.priorityBoarding] = fareClass.priorityBoarding
                it[FareClassTable.loungeAccess] = fareClass.loungeAccess
                it[FareClassTable.carryOnAllowed] = fareClass.carryOnAllowed
                it[FareClassTable.carryOnWeightKg] = fareClass.carryOnWeightKg
                it[FareClassTable.checkedBaggagePieces] = fareClass.checkedBaggagePieces
                it[FareClassTable.checkedBaggageWeightKg] = fareClass.checkedBaggageWeightKg
                it[FareClassTable.milesEarnRate] = fareClass.milesEarnRate
                it[FareClassTable.minimumMilesForBooking] = fareClass.minimumMilesForBooking
                it[FareClassTable.description] = fareClass.description
                it[FareClassTable.createdAt] = Instant.now().toString()
                it[FareClassTable.updatedAt] = fareClass.updatedAt
            }
            true
        }

    /**
     * Deletes a fare class by searching with it's ID
     * @param id fare class id
     */
    fun deleteByID(id: Int) =
        transaction {
            FareClassTable.deleteWhere { FareClassTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id fare class id
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
                FareClassTable.update({ FareClassTable.id eq id }) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
