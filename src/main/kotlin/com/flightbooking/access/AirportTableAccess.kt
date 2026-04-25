package com.flightbooking.access // we want to be able to access all access files from a single package "access"

import com.flightbooking.models.Airport
import com.flightbooking.models.toAirport
import com.flightbooking.tables.AirportTable

import org.jetbrains.exposed.sql.selectAll

// other function imports that will likely be used
// there is a bunch more information here: 
// https://www.jetbrains.com/help/exposed/dsl-statement-builder.html
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder

// class instance/reference of the airport table
class AirportTableAccess {
    // specific search functions for each table (pretty much copy and pasted for most)
    fun getAll(): List<Airport> = transaction {
        AirportTable.selectAll().map {
            it.toAirport()
        }
    }
    
    fun <T> getByAttribute(attribute: Column<T>, value: T): List<Airport> = transaction {
        //accepts attribute you're searching by and the value you want it to be
        AirportTable.select { attribute eq value } 
            .map { it.toAirport() } 
    }

    fun getAirportCodeByOrigin(origin: String): String? = transaction {
        AirportTable.select {
            (AirportTable.iataCode like "%$origin%") or
            (AirportTable.city like "%$origin%") or
            (AirportTable.name like "%$origin%")
        }.firstOrNull()?.get(AirportTable.iataCode)
    }

    fun getCityByOrigin(origin: String): String? = transaction {
        AirportTable.select {
            (AirportTable.iataCode like "%$origin%") or
            (AirportTable.city like "%$origin%") or
            (AirportTable.name like "%$origin%")
        }.firstOrNull()?.get(AirportTable.city)
    }

    fun createAirport(
        iataCode: String, 
        name: String?, 
        city: String?, 
        country: String?
        ): Boolean = transaction { 
        // inserts new record into the table and returns the generated id
        AirportTable.insert { 
            it[AirportTable.iataCode] = iataCode 
            it[AirportTable.name] = name 
            it[AirportTable.city] = city 
            it[AirportTable.country] = country 
        }
        true
    }

    fun deleteByID(id: Int) = transaction { 
        // deletes record by id
        AirportTable.deleteWhere { AirportTable.id eq id } 
    }

    fun <T> updateRecordByAttribute(id: Int, column: Column<T>, value: T): Boolean = transaction { 
        //updates the record with given id, given column and value
        val rows = AirportTable.update({ AirportTable.id eq id }) { 
            stmt -> stmt[column] = value 
        } 
        rows > 0 
    }

    fun upsertByIata(airport: Airport) = transaction {
        //used for the db import
        val existing = AirportTable
            .select {AirportTable.iataCode eq airport.iataCode}
            .singleOrNull()
        if (existing == null) {
            AirportTable.insert {
                it[iataCode] = airport.iataCode
                it[name] = airport.name
                it[AirportTable.city] = airport.city
                it[AirportTable.country] = airport.country
            }
        } else {
            AirportTable.update({ AirportTable.iataCode eq airport.iataCode }) {
                it[name] = airport.name
                it[city] = airport.city
                it[country] = airport.country
            }
        }
    }
}
