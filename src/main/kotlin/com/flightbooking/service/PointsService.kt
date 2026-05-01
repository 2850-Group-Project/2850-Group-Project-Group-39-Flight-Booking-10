package com.flightbooking.service

import com.flightbooking.access.PointsTableAccess

/**
 * Business rules for the loyalty points system.
 *
 *  Earning  : 1 pt per £1 spent × fare class milesEarnRate  (floor to Int)
 *  Redeeming: 100 pts = £1 discount
 */
object PointsService {
    private const val POINTS_PER_POUND: Double = 1.0
    private const val POUNDS_PER_POINT: Double = 0.01 // 100 pts = £1

    private val pointsTable = PointsTableAccess()

    fun getBalance(userId: Int): Int =
        pointsTable.getBalance(userId)?.balance ?: 0
    
    /**
     * Call when booking is confirmed/paid.
     *
     * @param userId      the logged-in user
     * @param bookingId   for audit trail
     * @param amountPaid  the actual GBP amount charged (after any discount)
     * @param milesEarnRate  multiplier from FareClass (default 1.0)
     */
    fun awardPointsForBooking(
        userId: Int,
        bookingId: Int,
        amountPaid: Double,
        milesEarnRate: Double = 1.0,
    ): Int {
        val earned = (amountPaid * POINTS_PER_POUND * milesEarnRate).toInt()
        if (earned <= 0) return getBalance(userId)

        return pointsTable.addPoints(
            userId = userId,
            points = earned,
            bookingId = bookingId,
            type = "earn",
            description = "Points earned for booking #$bookingId",
        ) 
    }

    /**
     * Calculates the maximum GBP discount available for [userId] on a booking
     * totalling [bookingTotal], without actually deducting anything.
     *
     * @return Pair(pointsNeeded, discountAmount)
     */
    fun calculateRedemption(userId: Int, bookingTotal: Double): Pair<Int, Double> {
        val balance = getBalance(userId)
        val balanceAsGBP = balance * POUNDS_PER_POINT
        val discount= minOf(balanceAsGBP, bookingTotal)
        return Pair(balance, discount)
    }

    /**
     * Deducts points when the user opts in to using them at checkout.
     * Returns the GBP value of the discount applied.
     *
     * @param pointsToRedeem pass in the value from [calculateRedemption] so the
     *                       caller controls exactly how many to use.
     */
    fun redeemPoints(
        userId: Int,
        bookingId: Int,
        pointsToRedeem: Int,
        bookingTotal: Double,
    ): Double {
        require(pointsToRedeem > 0) { "Must redeem at least 1 point" }
        
        val discountValue = (pointsToRedeem * POUNDS_PER_POINT).coerceAtMost(bookingTotal)
        
        pointsTable.deductPoints(
            userId = userId,
            points = pointsToRedeem,
            bookingId = bookingId,
            description = "Points redeemed for booking #$bookingId (£%.2f discount)".format(discountValue)
        )
        return discountValue
    }
}
