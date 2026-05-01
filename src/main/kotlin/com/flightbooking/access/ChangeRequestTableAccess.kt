package com.flightbooking.access

import com.flightbooking.mappers.toChangeRequest
import com.flightbooking.models.ChangeRequest
import com.flightbooking.tables.ChangeRequestTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Class instance for using change request table
 */
class ChangeRequestTableAccess {
    /**
     * Gets list of all change requests
     */
    fun getAll(): List<ChangeRequest> =
        transaction {
            ChangeRequestTable.selectAll().map { it.toChangeRequest() }
        }

    /**
     * Gets list of change requests from DB, filtering by attribute and value you want it to be
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<ChangeRequest> =
        transaction {
            ChangeRequestTable
                .select { attribute eq value }
                .map { it.toChangeRequest() }
        }

    /**
     * Gets change request with corresponding id
     */
    fun findById(id: Int): ChangeRequest? =
        transaction {
            ChangeRequestTable
                .select { ChangeRequestTable.id eq id }
                .limit(1)
                .firstOrNull()
                ?.toChangeRequest()
        }

    /**
     * Creates a change request object
     */
    fun createRequest(request: ChangeRequest): Int =
        transaction {
            ChangeRequestTable.insert {
                it[ChangeRequestTable.userId] = request.userId
                it[ChangeRequestTable.bookingId] = request.bookingId
                it[ChangeRequestTable.bookingSegmentId] = request.bookingSegmentId
                it[ChangeRequestTable.reason] = request.reason
                it[ChangeRequestTable.status] = "pending"
                it[ChangeRequestTable.currentFlightId] = request.currentFlightId
                it[ChangeRequestTable.requestedFlightId] = request.requestedFlightId
                it[ChangeRequestTable.requestedSeatId] = request.requestedSeatId
                it[ChangeRequestTable.createdAt] = Instant.now().toString()
                it[ChangeRequestTable.updatedAt] = Instant.now().toString()
            } get ChangeRequestTable.id
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
                ChangeRequestTable.update({ ChangeRequestTable.id eq id }) { stmt ->
                    stmt[column] = value
                    stmt[ChangeRequestTable.updatedAt] = Instant.now().toString()
                }
            rows > 0
        }

    /**
     * Updates change request with the id's status
     */
    fun updateStatus(
        id: Int,
        status: String,
    ): Boolean =
        transaction {
            val rows =
                ChangeRequestTable.update({ ChangeRequestTable.id eq id }) {
                    it[ChangeRequestTable.status] = status
                    it[ChangeRequestTable.updatedAt] = Instant.now().toString()
                }
            rows > 0
        }

    /**
     * Deletes an Airport by searching with it's ID
     */
    fun deleteById(id: Int): Boolean =
        transaction {
            ChangeRequestTable.deleteWhere { ChangeRequestTable.id eq id } > 0
        }
}
