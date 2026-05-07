package com.flightbooking.service

import com.flightbooking.access.PointsTableAccess
import com.flightbooking.models.UserPoints
import com.flightbooking.tables.FareClassTable
import com.flightbooking.tables.FlightFareTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Business rules for the loyalty points system.
 *
 *  Earning  : 1 pt per £1 spent × fare class earn rate x membership rate  (floor to Int)
 *  Redeeming: 100 pts = discount based on balance * membership rate
 */
object PointsService {
    private val pointsTable = PointsTableAccess()

    /**
     * Returns the user's current points balance, or zero when no points row exists yet.
     */
    fun getBalance(userId: Int): Int = pointsTable.getBalance(userId)?.balance ?: 0

    /**
     * Call when booking is confirmed/paid.
     * @param userId      the logged-in user
     * @param bookingId   for audit trail
     * @param amountPaid  the actual GBP amount charged (after any discount)
     * @param milesEarnRate  multiplier from FareClass (default 1.0)
     */
    fun awardPointsForBooking(
        userId: Int,
        bookingId: Int,
        amountPaid: Double,
        fareEarnRate: Double,
    ): Int {
        val row =
            getUserPointsRow(userId) ?: error(
                "Failed to create points record for userId: $userId",
            )

        val earned =
            calculateEarning(
                amountPaid = amountPaid,
                fareEarnRate = fareEarnRate,
                membershipStatus = row.membershipStatus,
            )

        val newBalance =
            updateAwardUserPoints(
                userId = userId,
                earned = earned,
                currentBalance = row.balance,
                currentTotal = row.totalPointsEarned,
            )

        pointsTable.addPoints(
            userId = userId,
            points = earned,
            bookingId = bookingId,
            type = "earn",
            description = "Points earned for booking #$bookingId",
        )

        return newBalance
    }

    /**
     * Calculates the maximum GBP discount available for [userId] on a booking
     * totalling [bookingTotal], without actually deducting anything.
     * gets minimum between max discount calculated and booking total
     * because discount < booking total
     * @return Pair(, discount)
     */
    fun calculateRedemption(
        userId: Int,
        bookingTotal: Double,
    ): Pair<Int, Double> {
        val row =
            pointsTable.getBalance(userId)
                ?: return Pair(0, 0.0)
        val rate = discountRateForTier(row.membershipStatus)
        val maxDiscount = row.balance * rate
        val discount = minOf(maxDiscount, bookingTotal)
        return Pair(row.balance, discount)
    }

    /**
     * Deducts points when the user opts in to using them at checkout.
     * Returns the GBP value of the discount applied.
     *
     * @param pointsToRedeem pass in the value from [calculateRedemption] so the
     * caller controls exactly how many to use.
     */
    fun redeemPoints(
        userId: Int,
        bookingId: Int,
        pointsToRedeem: Int,
        bookingTotal: Double,
    ): Double {
        require(pointsToRedeem > 0) { "Must redeem at least 1 point" }

        val row =
            getUserPointsRow(userId)
                ?: error("Failed creating points record for userId: $userId")
        val rate = discountRateForTier(row.membershipStatus)
        val discountValue = (pointsToRedeem * rate).coerceAtMost(bookingTotal)

        pointsTable.deductPoints(
            userId = userId,
            points = pointsToRedeem,
            bookingId = bookingId,
            description = "Points redeemed for booking #$bookingId (£%.2f discount)".format(discountValue),
        )
        return discountValue
    }

    /**
     * Updates the balance and total spent of a user with earned amount
     * @param userId
     * @param earned difference to add
     * @param currentBalance
     * @param currentTotal
     * @return the new record
     */
    private fun updateAwardUserPoints(
        userId: Int,
        earned: Int,
        currentBalance: Int,
        currentTotal: Int,
    ): Int {
        val newBalance = currentBalance + earned
        val newTotal = currentTotal + earned

        val newStatus = calculateMembershipStatus(newTotal)

        pointsTable.updatePointsAndStatus(
            userId = userId,
            balance = newBalance,
            totalPointsEarned = newTotal,
            membershipStatus = newStatus,
        )

        return newBalance
    }

    /**
     * Gets the miles earn rate for a flight fare
     * @param outboundFareId fare id
     * @return miles earn rate
     */
    fun fetchMilesEarnRate(outboundFareId: Int?): Double {
        if (outboundFareId == null) return 1.0
        return transaction {
            (FlightFareTable innerJoin FareClassTable)
                .select { FlightFareTable.id eq outboundFareId }
                .singleOrNull()
                ?.get(FareClassTable.milesEarnRate)
                ?: 1.0
        }
    }

    /**
     * Gets the record of the user's points, if it exists,
     * creates a new record if it doesn't exist
     * @param userId of user points record we want
     * @return the user points record
     */
    fun getUserPointsRow(userId: Int): UserPoints? {
        val exists = pointsTable.getBalance(userId)
        if (exists != null) return exists

        pointsTable.addPoints(
            userId = userId,
            points = 0,
            bookingId = null,
            type = "init",
            description = "Initial points record creation",
        )

        return pointsTable.getBalance(userId)
    }
}
