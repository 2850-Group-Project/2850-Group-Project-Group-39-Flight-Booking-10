package com.flightbooking.service

private const val GOLD_DISCOUNT_RATE: Double = 0.10
private const val SILVER_DISCOUNT_RATE: Double = 0.05
private const val BRONZE_DISCOUNT_RATE: Double = 0.025

private const val GOLD_EARN_RATE: Double = 1.0
private const val SILVER_EARN_RATE: Double = 0.75
private const val BRONZE_EARN_RATE: Double = 0.5

private const val GOLD_MEMBER_THRESHOLD: Int = 10000
private const val SILVER_MEMBER_THRESHOLD: Int = 5000

/**
 * Determines the discount rate you get based on status
 * @param status membership status
 * @return discount rate
 */
fun discountRateForTier(status: String): Double =
    when (status) {
        "Gold" -> GOLD_DISCOUNT_RATE
        "Silver" -> SILVER_DISCOUNT_RATE
        "Bronze" -> BRONZE_DISCOUNT_RATE
        else -> 0.0
    }

/**
 * Determines the miles earning rate based on status
 * @param status membership status
 * @return exchange rate
 */
fun milesRateForTier(status: String): Double =
    when (status) {
        "Gold" -> GOLD_EARN_RATE
        "Silver" -> SILVER_EARN_RATE
        "Bronze" -> BRONZE_EARN_RATE
        else -> 0.0
    }

/**
 * Determines your membership status based on total points earned
 * @param totalPoints total points earned
 * @return new status
 */
fun calculateMembershipStatus(totalPoints: Int): String =
    when {
        totalPoints >= GOLD_MEMBER_THRESHOLD -> "Gold"
        totalPoints >= SILVER_MEMBER_THRESHOLD -> "Silver"
        else -> "Bronze"
    }

/**
 * Calculates number of points earned based on the amount of money
 * that the user has spent and membership status
 *
 * @return Int
 */
fun calculateEarning(
    amountPaid: Double,
    fareEarnRate: Double,
    membershipStatus: String,
): Int {
    val tierMultiplier = milesRateForTier(membershipStatus)
    return (amountPaid * fareEarnRate * tierMultiplier).toInt()
}
