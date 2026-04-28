package com.flightbooking.access

import com.flightbooking.models.Notification
import com.flightbooking.mappers.toNotification
import com.flightbooking.tables.NotificationTable

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import java.time.Instant

class NotificationTableAccess {
    fun getAll(): List<Notification> = transaction {
        NotificationTable.selectAll().map {
            it.toNotification()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Notification> = transaction {
        NotificationTable.select { attribute eq value } 
            .map { it.toNotification() } 
    }
    fun createNotification(
        userId: Int?, 
        type: String?, 
        message: String?, 
        readAt: String?
        ): Boolean = transaction { 
        NotificationTable.insert { 
            it[NotificationTable.userId] = userId
            it[NotificationTable.type] = type
            it[NotificationTable.message] = message
            it[NotificationTable.createdAt] = java.time.Instant.now().toString()
            it[NotificationTable.readAt] = readAt
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        NotificationTable.deleteWhere { NotificationTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = NotificationTable.update(
            { NotificationTable.id eq id }
            ) { stmt ->
            stmt[column] = value } 
        rows > 0 
    }
}
