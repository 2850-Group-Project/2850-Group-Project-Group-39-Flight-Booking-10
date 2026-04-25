package com.flightbooking.access

import com.flightbooking.models.User
import com.flightbooking.models.toUser
import com.flightbooking.tables.UserTable

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder

import java.time.Instant

import com.flightbooking.service.AuthService
import com.flightbooking.constants.MIN_AGE
import com.flightbooking.constants.MAX_AGE
import com.flightbooking.constants.MAX_MONTHS
import com.flightbooking.constants.MAX_DAYS

class UserTableAccess {
    fun getAll(): List<User> = transaction {
        UserTable.selectAll().map {
            it.toUser()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<User> = transaction {
        UserTable.select { attribute eq value } 
            .map { it.toUser() } 
    }
    fun createUser(
        email: String,
        passwordHash: String,
        firstName: String?,
        lastName: String?
    ): Boolean = transaction {
        val exists = UserTable.select { UserTable.email eq email }.count() > 0
        if (exists) return@transaction false

        UserTable.insert {
            it[UserTable.email] = email
            it[UserTable.passwordHash] = passwordHash
            it[UserTable.firstName] = firstName
            it[UserTable.lastName] = lastName
            it[UserTable.phoneNumber] = null
            it[UserTable.dateOfBirth] = null
            it[UserTable.createdAt] = java.time.Instant.now().toString()
            it[UserTable.accountStatus] = "active"
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        UserTable.deleteWhere { UserTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = UserTable.update({ UserTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    fun findByEmail(email: String): User? = transaction {
        UserTable.select { UserTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let { it.toUser() }
    }
    fun generateUsers(count: Int = 200) {
        val firstNames = listOf(
            "Liam","Noah","Oliver","Elijah","James",
            "Emma","Olivia","Ava","Sophia","Isabella",
            "Lucas","Mason","Ethan","Logan","Aiden",
            "Amelia","Mia","Charlotte","Harper","Evelyn"
        )
        val lastNames = listOf(
            "Smith","Johnson","Williams","Brown","Jones",
            "Garcia","Miller","Davis","Rodriguez","Martinez",
            "Hernandez","Lopez","Gonzalez","Wilson","Anderson",
            "Thomas","Taylor","Moore","Jackson","Martin"
        )
        val namePairs = firstNames.flatMap { first ->
            lastNames.map { last -> first to last}
        }.shuffled()
        val limitedPairs = namePairs.take(count)
        val startId = transaction {
            UserTable.selectAll().count() + 1
        }
        limitedPairs.forEachIndexed { index, (first, last) ->
            val id = startId + index

            val email = "${first.lowercase()}.${last.lowercase()}@gmail.com"
            val password = "UserPass%02d".format(id)
            AuthService.register(
                email = email,
                password = password,
                firstName = first,
                lastName = last
            )
            val dob = java.time.LocalDate.now()
                .minusYears((MIN_AGE..MAX_AGE).random().toLong())
                .minusMonths((0..MAX_MONTHS).random().toLong())
                .minusDays((0..MAX_DAYS).random().toLong())
                .toString()

            transaction {
                UserTable.update({ UserTable.email eq email }) {
                    it[UserTable.dateOfBirth] = dob
                }
            }
        }
    }
    fun fillMissingPhoneNumbers() = transaction {
        val users = UserTable.select { UserTable.phoneNumber.isNull() }.toList()

        users.forEachIndexed { _, row ->
            val id = row[UserTable.id]
            val phone = "07700" + "%06d".format(id)

            UserTable.update({ UserTable.id eq id }) {
                it[phoneNumber] = phone
            }
        }
    }

}
