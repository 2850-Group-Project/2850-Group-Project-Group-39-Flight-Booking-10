package com.flightbooking.access

import com.flightbooking.models.Payment
import com.flightbooking.models.toPayment
import com.flightbooking.tables.PaymentTable

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

import org.jetbrains.exposed.sql.insertAndGetId

import com.flightbooking.tables.BookingTable
import com.flightbooking.tables.BookingSegmentTable
import com.flightbooking.tables.FlightFareTable

import com.flightbooking.constants.TIMESTAMP_DAYS_UPPER_LIMIT
import com.flightbooking.constants.TIMESTAMP_HOURS_UPPER_LIMIT
import com.flightbooking.constants.TIMESTAMP_MINUTES_UPPER_LIMIT

const val RAND_UPPER : Int = 100
const val STATUS_THRESHOLD : Int = 90
const val RAND_REF_LOWER : Int = 1000
const val RAND_REF_UPPER : Int = 9999

class PaymentTableAccess {
    fun getAll(): List<Payment> = transaction {
        PaymentTable.selectAll().map {
            it.toPayment()
        }
    }
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Payment> = transaction {
        PaymentTable.select { attribute eq value } 
            .map { it.toPayment() } 
    }

    @Suppress("LongParameterList")
    fun createPayment(
        bookingId: Int, 
        amount: Double?, 
        paymentMethod: String?, 
        paymentStatus: String, 
        paidAt: String?,
        providerReference: String?,
        currency: String
        ): Boolean = transaction { 
        PaymentTable.insert { 
            it[PaymentTable.bookingId] = bookingId
            it[PaymentTable.amount] = amount
            it[PaymentTable.paymentMethod] = paymentMethod
            it[PaymentTable.paymentStatus] = paymentStatus
            it[PaymentTable.paidAt] = paidAt
            it[PaymentTable.providerReference] = providerReference
            it[PaymentTable.currency] = currency
        }
        true
    }
    fun deleteByID(id: Int) = transaction { 
        PaymentTable.deleteWhere { PaymentTable.id eq id } }
    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        val rows = PaymentTable.update({ PaymentTable.id eq id }) { 
            stmt -> stmt[column] = value } 
        rows > 0 }
    fun generatePayments() = transaction {
        println("genreasting payments")
        val bookings = BookingTable.selectAll().toList()
        fun randomTimeStamp(): String = 
            java.time.LocalDateTime.now()
                .minusDays((0..TIMESTAMP_DAYS_UPPER_LIMIT).random().toLong())
                .minusHours((0..TIMESTAMP_HOURS_UPPER_LIMIT).random().toLong())
                .minusMinutes((0..TIMESTAMP_MINUTES_UPPER_LIMIT).random().toLong())
                .toString()
        fun randomStatus(): String = 
            if ((1..RAND_UPPER).random() <= STATUS_THRESHOLD) "paid" else "refunded"
        fun providerRef():String = 
            listOf("STR", "PPL", "WDP", "APY").random() + "-" + (RAND_REF_LOWER..RAND_REF_UPPER).random()

        bookings.forEach { bookingRow ->
            val bookingId = bookingRow[BookingTable.id]
            val segment = BookingSegmentTable
                .select { BookingSegmentTable.bookingId eq bookingId }
                .firstOrNull()
            if (segment == null) {
                println("$bookingId has no segment - skip")
                return@forEach
            }

            val fareId = segment[BookingSegmentTable.flightFareId]
            val fareRow = FlightFareTable
                .select { FlightFareTable.id eq fareId }
                .first()
            val price = fareRow[FlightFareTable.price]
            val status = randomStatus()

            val paymentId = PaymentTable.insert {
                it[PaymentTable.bookingId] = bookingId
                it[PaymentTable.amount] = price
                it[PaymentTable.paymentMethod] = listOf("credit","debit","paypal","apple_pay").random()
                it[PaymentTable.paymentStatus] = status
                it[PaymentTable.paidAt] = if (status == "paid") randomTimeStamp() else null
                it[PaymentTable.providerReference] = providerRef()
                it[PaymentTable.currency] = "GBP"
            } get PaymentTable.id
            BookingTable.update({ BookingTable.id eq bookingId }) {
                it[BookingTable.paymentId] = paymentId
            }
        }
        println("done generating")
    }
}
