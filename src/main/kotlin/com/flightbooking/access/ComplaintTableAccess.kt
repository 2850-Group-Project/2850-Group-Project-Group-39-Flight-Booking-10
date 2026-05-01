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

/**
 * Class instance for using complaint table
 */
class ComplaintTableAccess {
    /**
     * Gets list of all complaints
     * @return list of complaints
     */
    fun getAll(): List<Complaint> =
        transaction {
            ComplaintTable.selectAll().map {
                it.toComplaint()
            }
        }

    /**
     * Gets list of complaints from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of complaints
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Complaint> =
        transaction {
            ComplaintTable.select { attribute eq value }
                .map { it.toComplaint() }
        }

    /**
     * Gets list of complaints by UserId
     * @param userId user id
     * @return list of complaints
     */
    fun findByUserId(userId: Int): List<Complaint> {
        return transaction {
            ComplaintTable
                .select { ComplaintTable.userId eq userId }
                .orderBy(ComplaintTable.createdAt, SortOrder.DESC)
                .map { it.toComplaint() }
        }
    }

    /**
     * Creates a complaint object
     * @param userId user id
     * @param type complaint type
     * @param message complaint message
     * @param status complaint status
     * @param handledByStaffId staff id
     * @return true if created
     */
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
                it[ComplaintTable.createdAt] = Instant.now().toString()
                it[ComplaintTable.status] = status
                it[ComplaintTable.handledByStaffId] = handledByStaffId
            }
            true
        }

    /**
     * Deletes a complaint by searching with it's ID
     * @param id complaint id
     */
    fun deleteByID(id: Int) =
        transaction {
            ComplaintTable.deleteWhere { ComplaintTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id complaint id
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
                ComplaintTable.update(
                    { ComplaintTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
