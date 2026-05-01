package com.flightbooking.access

import com.flightbooking.mappers.toPayment
import com.flightbooking.models.Payment
import com.flightbooking.tables.PaymentTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Class instance for using payment table
 */
class PaymentTableAccess {

    /**
     * Gets list of all payments
     * @return list of payments
     */
    fun getAll(): List<Payment> =
        transaction {
            PaymentTable.selectAll().map {
                it.toPayment()
            }
        }

    /**
     * Gets list of payments from DB, filtering by attribute and value you want it to be
     * @param attribute column to filter
     * @param value value to match
     * @return list of payments
     */
    fun <T> getByAttribute(
        attribute: Column<T>,
        value: T,
    ): List<Payment> =
        transaction {
            PaymentTable.select { attribute eq value }
                .map { it.toPayment() }
        }

    /**
     * Creates a payment object
     * @param payment payment model
     * @return created payment id
     */
    fun createPayment(payment: Payment): Int =
        transaction {
            PaymentTable.insert {
                it[PaymentTable.bookingId] = payment.bookingId
                it[PaymentTable.amount] = payment.amount
                it[PaymentTable.paymentMethod] = payment.paymentMethod
                it[PaymentTable.paymentStatus] = payment.paymentStatus
                it[PaymentTable.paidAt] = payment.paidAt
                it[PaymentTable.providerReference] = payment.providerReference
                it[PaymentTable.currency] = payment.currency
            }[PaymentTable.id]
        }

    /**
     * Deletes a payment by searching with it's ID
     * @param id payment id
     */
    fun deleteByID(id: Int) =
        transaction {
            PaymentTable.deleteWhere { PaymentTable.id eq id }
        }

    /**
     * Updates a record's attribute with a value passed in
     * @param id payment id
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
                PaymentTable.update(
                    { PaymentTable.id eq id },
                ) { stmt ->
                    stmt[column] = value
                }
            rows > 0
        }
}
