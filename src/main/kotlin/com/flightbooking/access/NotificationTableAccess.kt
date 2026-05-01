package com.flightbooking.access

import com.flightbooking.mappers.toNotification
import com.flightbooking.models.Notification
import com.flightbooking.tables.NotificationTable
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
 * Class instance for using notificaiton table
 */
class NotificationTableAccess {
    /**
     * Gets list of all notifications
     * @return list of notifications
     */
    fun getAll(): List<Notification> =
        transaction {
            NotificationTable.selectAll().map {
                it.toNotification()
            }
        }

    /**
     * Gets list of notifications from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of notifications
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Notification> =
        transaction {
            NotificationTable.select { attribute eq value }
                .map { it.toNotification() }
        }

    /**
     * Creates a notification object
     * @param userId user id
     * @param type notification type
     * @param message notification message
     * @param readAt read timestamp
     * @return true if created
     */
    fun createNotification(
        userId: Int?,
        type: String?,
        message: String?,
        readAt: String?,
    ): Boolean =
        transaction {
            NotificationTable.insert {
                it[NotificationTable.userId] = userId
                it[NotificationTable.type] = type
                it[NotificationTable.message] = message
                it[NotificationTable.createdAt] = Instant.now().toString()
                it[NotificationTable.readAt] = readAt
            }
            true
        }

    /**
     * Deletes a notification by searching with it's ID
     * @param id notification id
     */
    fun deleteByID(id: Int) =
        transaction {
            NotificationTable.deleteWhere { NotificationTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id notification id
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
                NotificationTable.update(
                    { NotificationTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
