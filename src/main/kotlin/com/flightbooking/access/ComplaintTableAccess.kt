package com.flightbooking.access
import com.flightbooking.mappers.toComplaint
import com.flightbooking.models.Complaint
import com.flightbooking.tables.ComplaintTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ComplaintTableAccess {
    fun getAll(): List<Complaint> =
        transaction {
            ComplaintTable.selectAll().map {
                it.toComplaint()
            }
        }

    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Complaint> =
        transaction {
            ComplaintTable.select { attribute eq value }
                .map { it.toComplaint() }
        }

    fun findByUserId(userId: Int): List<Complaint> {
        return transaction {
            ComplaintTable
                .select { ComplaintTable.userId eq userId }
                .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
                .map { it.toComplaint() }
        }
    }

    fun createComplaint(
        userId: Int?,
        type: String?,
        message: String?,
        status: String,
        handledByStaffId: Int?,
    ): Boolean =
        transaction {
            ComplaintTable.insert {
                it[ComplaintTable.userId] = userId
                it[ComplaintTable.type] = type
                it[ComplaintTable.message] = message
                it[ComplaintTable.createdAt] = java.time.Instant.now().toString()
                it[ComplaintTable.status] = status
                it[ComplaintTable.handledByStaffId] = handledByStaffId
            }
            true
        }

    fun deleteByID(id: Int) =
        transaction {
            ComplaintTable.deleteWhere { ComplaintTable.id eq id }
        }

    fun <T> updateRecordByAttribute(
        id: Int,
        column: Column<T>,
        value: T,
    ): Boolean =
        transaction {
            val rows =
                ComplaintTable.update(
                    { ComplaintTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
