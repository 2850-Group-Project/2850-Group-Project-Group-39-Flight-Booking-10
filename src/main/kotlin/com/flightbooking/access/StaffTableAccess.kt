package com.flightbooking.access

import com.flightbooking.mappers.toStaff
import com.flightbooking.models.Staff
import com.flightbooking.tables.StaffTable
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

class StaffTableAccess {
    fun getAll(): List<Staff> =
        transaction {
            StaffTable.selectAll().map {
                it.toStaff()
            }
        }

    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Staff> =
        transaction {
            StaffTable.select { attribute eq value }
                .map { it.toStaff() }
        }

    fun createStaff(
        email: String,
        passwordHash: String,
        firstName: String?,
        lastName: String?,
        role: String?,
    ): Boolean =
        transaction {
            val exists = StaffTable.select { StaffTable.email eq email }.limit(1).any()
            if (exists) return@transaction false

            StaffTable.insert {
                it[StaffTable.email] = email
                it[StaffTable.passwordHash] = passwordHash
                it[StaffTable.firstName] = firstName
                it[StaffTable.lastName] = lastName
                it[StaffTable.phoneNumber] = null
                it[StaffTable.role] = role
                it[StaffTable.createdAt] = java.time.Instant.now().toString()
            }
            true
        }

    fun deleteByID(id: Int) =
        transaction {
            StaffTable.deleteWhere { StaffTable.id eq id }
        }

    fun <T> updateRecordByAttribute(
        id: Int,
        column: Column<T>,
        value: T,
    ): Boolean =
        transaction {
            val rows =
                StaffTable.update(
                    { StaffTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }

    fun findByEmail(email: String): Staff? =
        transaction {
            StaffTable.select { StaffTable.email eq email }
                .limit(1)
                .firstOrNull()
                ?.let { it.toStaff() }
        }

    fun findByStaffId(staffId: Int): List<Staff> {
        return transaction {
            StaffTable
                .select { StaffTable.id eq staffId }
                .orderBy(StaffTable.createdAt, SortOrder.DESC)
                .map { it.toStaff() }
        }
    }
}
