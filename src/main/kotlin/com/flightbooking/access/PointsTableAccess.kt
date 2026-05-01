package com.flightbooking.access

import com.flightbooking.models.PointsTransaction
import com.flightbooking.models.UserPoints
import com.flightbooking.tables.PointsTransactionTable
import com.flightbooking.tables.UserPointsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class PointsTableAccess {
    /**
     * Returns the current points balance row for a user, or null if none exists yet.
     * @param userId user id
     * @return user points or null
     */
    fun getBalance(userId: Int): UserPoints? =
        transaction {
            UserPointsTable.select { UserPointsTable.userId eq userId }
                .singleOrNull()
                ?.let {
                    UserPoints(
                        id = it[UserPointsTable.id],
                        userId = it[UserPointsTable.userId],
                        balance = it[UserPointsTable.balance],
                    )
                }
        }

    /**
     * Returns all transaction history for a user, newest first.
     * @param userId user id
     * @return list of transactions
     */
    fun getTransactions(userId: Int): List<PointsTransaction> =
        transaction {
            PointsTransactionTable.select { PointsTransactionTable.userId eq userId }
                .orderBy(PointsTransactionTable.createdAt, SortOrder.DESC)
                .map { row ->
                    PointsTransaction(
                        id = row[PointsTransactionTable.id],
                        userId = row[PointsTransactionTable.userId],
                        bookingId = row[PointsTransactionTable.bookingId],
                        type = row[PointsTransactionTable.type],
                        points = row[PointsTransactionTable.points],
                        balanceAfter = row[PointsTransactionTable.balanceAfter],
                        description = row[PointsTransactionTable.description],
                        createdAt = row[PointsTransactionTable.createdAt],
                    )
                }
        }

    /**
     * Atomically adds [points] to the user's balance and writes a transaction log row.
     * Creates the user_points row if it doesn't exist yet.
     * Returns the new balance.
     * @param userId user id
     * @param points points to add
     * @param bookingId booking id
     * @param type transaction type
     * @param description description text
     * @return new balance
     */
    fun addPoints(
        userId: Int,
        points: Int,
        bookingId: Int?,
        type: String,
        description: String?,
    ): Int =
        transaction {
            val existing =
                UserPointsTable.select { UserPointsTable.userId eq userId }
                    .singleOrNull()

            // If the user doesn't have a balance row yet, we create one with the new points as the initial balance.
            val newBalance =
                if (existing == null) {
                    UserPointsTable.insert {
                        it[UserPointsTable.userId] = userId
                        it[UserPointsTable.balance] = points
                    }
                    points
                } else { // Otherwise, we update the existing balance by adding the new points.
                    val updated = existing[UserPointsTable.balance] + points
                    UserPointsTable.update({ UserPointsTable.userId eq userId }) {
                        it[balance] = updated
                    }
                    updated
                }

            PointsTransactionTable.insert {
                it[PointsTransactionTable.userId] = userId
                it[PointsTransactionTable.bookingId] = bookingId
                it[PointsTransactionTable.type] = type
                it[PointsTransactionTable.points] = points
                it[PointsTransactionTable.balanceAfter] = newBalance
                it[PointsTransactionTable.description] = description
                it[PointsTransactionTable.createdAt] = Instant.now().toString()
            }

            newBalance
        }

    /**
     * Deducts [points] from the user's balance.
     * Throws [IllegalStateException] if the balance would go negative.
     * Returns the new balance.
     * @param userId user id
     * @param points points to deduct
     * @param bookingId booking id
     * @param description description text
     * @return new balance
     */
    fun deductPoints(
        userId: Int,
        points: Int,
        bookingId: Int?,
        description: String?,
    ): Int {
        require(points > 0) { "Points to deduct must be positive" }
        return transaction {
            val existing =
                UserPointsTable.select { UserPointsTable.userId eq userId }
                    .singleOrNull()
                    ?: error("User does not exist for userId: $userId")

            val current = existing[UserPointsTable.balance]
            check(current >= points) { "Insufficient points balance" }

            val newBalance = current - points
            UserPointsTable.update({ UserPointsTable.userId eq userId }) {
                it[balance] = newBalance
            }

            PointsTransactionTable.insert {
                it[PointsTransactionTable.userId] = userId
                it[PointsTransactionTable.bookingId] = bookingId
                it[PointsTransactionTable.type] = "redeem"
                it[PointsTransactionTable.points] = -points
                it[PointsTransactionTable.balanceAfter] = newBalance
                it[PointsTransactionTable.description] = description
                it[PointsTransactionTable.createdAt] = Instant.now().toString()
            }

            newBalance
        }
    }
}
