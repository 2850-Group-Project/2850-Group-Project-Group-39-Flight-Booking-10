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

/**
 * Class instance for using complaint table
 */
class StaffTableAccess {
    /**
     * Gets list of all staff
     */
    fun getAll(): List<Staff> =
        transaction {
            StaffTable.selectAll().map {
                it.toStaff()
            }
        }

    /**
     * Gets list of staff from DB, filtering by attribute and value you want it to be
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Staff> =
        transaction {
            StaffTable.select { attribute eq value }
                .map { it.toStaff() }
        }

    /**
     * Creates a staff object
     */
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

    /**
     * Deletes a staff members record by searching with it's ID
     */
    fun deleteByID(id: Int) =
        transaction {
            StaffTable.deleteWhere { StaffTable.id eq id }
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
                StaffTable.update(
                    { StaffTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }

    /**
     * Finds staff record by searching with email
     */
    fun findByEmail(email: String): Staff? =
        transaction {
            StaffTable.select { StaffTable.email eq email }
                .limit(1)
                .firstOrNull()
                ?.let { it.toStaff() }
        }

    /**
     * Finds staff record by searching with staffId
     */
    fun findByStaffId(staffId: Int): List<Staff> {
        return transaction {
            StaffTable
                .select { StaffTable.id eq staffId }
                .orderBy(StaffTable.createdAt, SortOrder.DESC)
                .map { it.toStaff() }
        }
    }
}
