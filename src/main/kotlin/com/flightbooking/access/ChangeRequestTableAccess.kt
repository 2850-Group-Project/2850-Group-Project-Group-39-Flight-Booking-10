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

class ChangeRequestTableAccess {
    fun getAll(): List<ChangeRequest> =
        transaction {
            ChangeRequestTable.selectAll().map { it.toChangeRequest() }
        }

    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<ChangeRequest> =
        transaction {
            ChangeRequestTable
                .select { attribute eq value }
                .map { it.toChangeRequest() }
        }

    fun findById(id: Int): ChangeRequest? =
        transaction {
            ChangeRequestTable
                .select { ChangeRequestTable.id eq id }
                .limit(1)
                .firstOrNull()
                ?.toChangeRequest()
        }

    @Suppress("LongParameterList")
    fun createRequest(
        userId: Int,
        bookingId: Int,
        segmentId: Int,
        reason: String?,
        currentFlightId: Int?,
        requestedFlightId: Int?,
        requestedSeatId: Int?,
    ): Int =
        transaction {
            ChangeRequestTable.insert {
                it[ChangeRequestTable.userId] = userId
                it[ChangeRequestTable.bookingId] = bookingId
                it[ChangeRequestTable.bookingSegmentId] = segmentId
                it[ChangeRequestTable.reason] = reason
                it[ChangeRequestTable.status] = "pending"
                it[ChangeRequestTable.currentFlightId] = currentFlightId
                it[ChangeRequestTable.requestedFlightId] = requestedFlightId
                it[ChangeRequestTable.requestedSeatId] = requestedSeatId
                it[ChangeRequestTable.createdAt] = Instant.now().toString()
                it[ChangeRequestTable.updatedAt] = Instant.now().toString()
            } get ChangeRequestTable.id
        }

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

    fun deleteById(id: Int): Boolean =
        transaction {
            ChangeRequestTable.deleteWhere { ChangeRequestTable.id eq id } > 0
        }
}
