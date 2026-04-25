package com.flightbooking.access

import com.flightbooking.models.FareClass
import com.flightbooking.models.toFareClass
import com.flightbooking.tables.FareClassTable

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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

class FareClassTableAccess {
    fun getAll(): List<FareClass> = transaction {
        FareClassTable.selectAll().map {
            it.toFareClass()
        }
    }
    fun <T : Any> getByAttribute(attribute: Column<T?>, value: T?): List<FareClass> = transaction {
        val condition = if (value == null) {
            attribute.isNull()
        } else {
            attribute eq value
        }

        FareClassTable.select { condition }
            .map { it.toFareClass() }
    }

    @Suppress("LongParameterList")
    fun createFareClass(
        classCode: String,
        cabinClass: String?, 
        displayName: String?,
        refundable: Int, 
        cancelProtocol: String,
        advancedSeatSelection: Int,
        priorityCheckin: Int,
        priorityBoarding: Int,
        loungeAccess: Int,
        carryOnAllowed: Int,
        carryOnWeightKg: Int,
        checkedBaggagePieces: Int,
        checkedBaggageWeightKg: Int,
        milesEarnRate: Double,
        minimumMilesForBooking: Int?,
        description: String?,
        updatedAt: String
        ):Boolean = transaction {
        FareClassTable.insert {
            it[FareClassTable.classCode] = classCode 
            it[FareClassTable.cabinClass] = cabinClass 
            it[FareClassTable.displayName] = displayName 
            it[FareClassTable.refundable] = refundable 
            it[FareClassTable.cancelProtocol] = cancelProtocol
            it[FareClassTable.advanceSeatSelection] = advancedSeatSelection 
            it[FareClassTable.priorityCheckin] = priorityCheckin 
            it[FareClassTable.priorityBoarding] = priorityBoarding 
            it[FareClassTable.loungeAccess] = loungeAccess 
            it[FareClassTable.carryOnAllowed] = carryOnAllowed 
            it[FareClassTable.carryOnWeightKg] = carryOnWeightKg 
            it[FareClassTable.checkedBaggagePieces] = checkedBaggagePieces 
            it[FareClassTable.checkedBaggageWeightKg] = checkedBaggageWeightKg 
            it[FareClassTable.milesEarnRate] = milesEarnRate
            it[FareClassTable.minimumMilesForBooking] = minimumMilesForBooking
            it[FareClassTable.description] = description
            it[FareClassTable.createdAt] = java.time.Instant.now().toString()
            it[FareClassTable.updatedAt] = updatedAt
        }
        true
    }

    fun deleteByID(id: Int) = transaction {
        FareClassTable.deleteWhere { FareClassTable.id eq id }
    }

    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction {
        val rows = FareClassTable.update({ FareClassTable.id eq id }) {
            stmt -> stmt[column] = value
        }
        rows > 0 
    }
}
