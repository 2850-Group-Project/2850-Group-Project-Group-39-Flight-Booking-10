package com.flightbooking.access

import com.flightbooking.tables.ComplaintResponseTable
import com.flightbooking.tables.ComplaintTable
import com.flightbooking.tables.StaffTable
import com.flightbooking.tables.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private const val COMPLAINT_LIST_LIMIT: Int = 50
private const val START_DATE_INDEX = 0
private const val END_DATE_INDEX = 16

/**
 * Class instance for using complaint and complaint response tables
 */
class ComplaintResponseTableAccess {
    /**
     * Gets all complaints joined with user info
     * @param q optional search text (complaint id or user email)
     * @return list of complaint maps
     */
    fun getComplaints(q: String = ""): List<Map<String, Any?>> =
        transaction {
            ComplaintTable
                .join(UserTable, JoinType.LEFT, additionalConstraint = {
                    ComplaintTable.userId eq UserTable.id
                })
                .join(StaffTable, JoinType.LEFT, additionalConstraint = {
                    ComplaintTable.handledByStaffId eq StaffTable.id
                })
                .select {
                    if (q.isBlank()) {
                        org.jetbrains.exposed.sql.Op.TRUE
                    } else {
                        q.toIntOrNull()?.let { ComplaintTable.id eq it }
                            ?: (UserTable.email like "%$q%")
                    }
                }
                .orderBy(ComplaintTable.id, SortOrder.DESC)
                .limit(COMPLAINT_LIST_LIMIT)
                .map { row ->
                    mapOf(
                        "complaintId" to row[ComplaintTable.id],
                        "userId" to row[ComplaintTable.userId],
                        "userEmail" to row.getOrNull(UserTable.email),
                        "type" to row[ComplaintTable.type],
                        "message" to row[ComplaintTable.message],
                        "createdAt" to row[ComplaintTable.createdAt],
                        "status" to row[ComplaintTable.status],
                        "handledByStaffId" to row[ComplaintTable.handledByStaffId],
                        "handledByStaffEmail" to row.getOrNull(StaffTable.email),
                    )
                }
        }

    /**
     * Gets all responses for a given complaint
     * @param complaintId complaint id
     * @return list of response maps
     */
    fun getResponsesForComplaint(complaintId: Int): List<Map<String, Any?>> =
        transaction {
            ComplaintResponseTable
                .join(StaffTable, JoinType.LEFT, additionalConstraint = {
                    ComplaintResponseTable.staffId eq StaffTable.id
                })
                .select { ComplaintResponseTable.complaintId eq complaintId }
                .orderBy(ComplaintResponseTable.id, SortOrder.ASC)
                .map { row ->
                    mapOf(
                        "responseId" to row[ComplaintResponseTable.id],
                        "complaintId" to row[ComplaintResponseTable.complaintId],
                        "staffId" to row[ComplaintResponseTable.staffId],
                        "staffEmail" to row.getOrNull(StaffTable.email),
                        "message" to row[ComplaintResponseTable.message],
                        "createdAt" to
                            row[ComplaintResponseTable.createdAt]
                                .toString()
                                .formatDate()
                                .orEmpty(),
                    )
                }
        }

    /**
     * Creates a response to a complaint
     * @param complaintId complaint id
     * @param staffId staff id
     * @param message response message
     * @param createdAt timestamp
     * @return true if created
     */
    fun createResponse(
        complaintId: Int,
        staffId: Int,
        message: String,
        createdAt: String,
    ): Boolean =
        transaction {
            ComplaintResponseTable.insert {
                it[ComplaintResponseTable.complaintId] = complaintId
                it[ComplaintResponseTable.staffId] = staffId
                it[ComplaintResponseTable.message] = message
                it[ComplaintResponseTable.createdAt] = createdAt
            }
            true
        }

    /**
     * Updates the status of a complaint
     * @param complaintId complaint id
     * @param status new status
     * @param handledByStaffId staff id handling the complaint
     * @return true if updated
     */
    fun updateComplaintStatus(
        complaintId: Int,
        status: String,
        handledByStaffId: Int,
    ): Boolean =
        transaction {
            val rows =
                ComplaintTable.update({ ComplaintTable.id eq complaintId }) {
                    it[ComplaintTable.status] = status
                    it[ComplaintTable.handledByStaffId] = handledByStaffId
                }
            rows > 0
        }

    /**
     * Deletes a complaint response by id
     * @param responseId response id
     */
    fun deleteResponse(responseId: Int) =
        transaction {
            ComplaintResponseTable.deleteWhere { ComplaintResponseTable.id eq responseId }
        }

    /**
     * Deletes a complaint by id
     * @param complaintId complaint id
     */
    fun deleteComplaint(complaintId: Int) =
        transaction {
            ComplaintTable.deleteWhere { ComplaintTable.id eq complaintId }
        }

    /**
     * Formats date to remove unnecessary seconds/milliseconds
     * @param this: String
     * @return formatted date: String
     */
    private fun String.formatDate(): String {
        return this.replace("T", " ")
            .takeIf { it.length >= END_DATE_INDEX }
            ?.substring(START_DATE_INDEX, END_DATE_INDEX)
            ?: this
    }
}
