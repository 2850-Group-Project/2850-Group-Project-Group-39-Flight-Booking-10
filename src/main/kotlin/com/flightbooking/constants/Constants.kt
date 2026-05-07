package com.flightbooking.constants

/**
 * Maximum length for stored IATA airport codes.
 */
const val IATA_CODE_LENGTH: Int = 10

/**
 * Standard maximum length for general text fields.
 */
const val STANDARD_FIELD_LENGTH: Int = 255

/**
 * Maximum length for short text fields such as status and phone values.
 */
const val SHORT_FIELD_LENGTH: Int = 50

/**
 * Maximum length for fare class codes.
 */
const val CLASS_CODE_LENGTH: Int = 10

/**
 * Maximum length for currency codes stored in the database.
 */
const val CURRENCY_LENGTH: Int = 10

/**
 * Maximum length for stored date-of-birth strings.
 */
const val DOB_LENGTH: Int = 20

/**
 * Maximum length for passenger title strings.
 */
const val PASSENGER_TITLE_LENGTH: Int = 20

/**
 * Maximum length for gender strings.
 */
const val GENDER_LENGTH: Int = 10

/**
 * Maximum length for travel document numbers.
 */
const val DOCUMENT_NUMBER_LENGTH: Int = 100

/**
 * Maximum length for document expiry strings.
 */
const val DOCUMENT_EXPIRY_LENGTH: Int = 20

/**
 * Maximum length for document country codes.
 */
const val DOCUMENT_COUNTRY_LENGTH: Int = 10

/**
 * Shared length for shorter text fields.
 */
const val SHORTER_FIELD_LENGTH: Int = 20

/**
 * Shared length for the shortest text fields.
 */
const val SHORTEST_FIELD_LENGTH: Int = 10

/**
 * Default carry-on weight allowance in kilograms.
 */
const val DEFAULT_CARRY_ON_WEIGHT_ALLOWED: Int = 7

/**
 * Default unread or unviewed marker for integer-backed flags.
 */
const val DEFAULT_VIEWED_STATUS: Int = 0

/**
 * Minimum passenger age accepted by staff-side generated data.
 */
const val MIN_AGE = 18

/**
 * Maximum passenger age accepted by staff-side generated data.
 */
const val MAX_AGE = 80

/**
 * Highest zero-based month offset used for generated dates.
 */
const val MAX_MONTHS = 11

/**
 * Highest zero-based day offset used for generated dates.
 */
const val MAX_DAYS = 27

/**
 * Base multiplier for economy fare pricing.
 */
const val FARE_CLASS_ECONOMY_MULTIPLIER = 1.0

/**
 * Base multiplier for economy plus fare pricing.
 */
const val FARE_CLASS_ECONOMY_PLUS_MULTIPLIER = 1.2

/**
 * Base multiplier for business fare pricing.
 */
const val FARE_CLASS_BUSINESS_MULTIPLIER = 2.0

/**
 * Base multiplier for premium economy fare pricing.
 */
const val FARE_CLASS_PREMIUM_ECONOMY_MULTIPLIER = 1.5

/**
 * Base multiplier for first class fare pricing.
 */
const val FARE_CLASS_FIRST_MULTIPLIER = 1.8

/**
 * Database id for the default economy fare class.
 */
const val FARE_CLASS_ECONOMY_ID = 1

/**
 * Database id for the default economy plus fare class.
 */
const val FARE_CLASS_ECONOMY_PLUS_ID = 2

/**
 * Database id for the default business fare class.
 */
const val FARE_CLASS_BUSINESS_ID = 3

/**
 * Database id for the default premium economy fare class.
 */
const val FARE_CLASS_PREMIUM_ECONOMY_ID = 4

/**
 * Database id for the default first class fare class.
 */
const val FARE_CLASS_FIRST_ID = 5

/**
 * Offset applied when deriving a base fare price.
 */
const val BASE_PRICE_OFFSET = 40

/**
 * Modulus used when deriving a flight-specific base price.
 */
const val BASE_PRICE_FLIGHT_MOD = 50

/**
 * Multiplier applied when deriving generated fare prices.
 */
const val BASE_PRICE_MULTIPLIER = 1.2

/**
 * Default capacity used when flight capacity is missing.
 */
const val DEFAULT_CAPACITY = 100

/**
 * Offset used when deriving available seats from flight capacity.
 */
const val SEATS_DIVIDER_OFFSET = 2

/**
 * Minimum available seats assigned to generated fares.
 */
const val MIN_SEATS_AVAILABLE = 5

/**
 * Search window size around a requested flight date.
 */
const val DAYS_BEFORE_AND_AFTER_TO_SHOW: Long = 5

/**
 * Upper limit for generated timestamp day offsets.
 */
const val TIMESTAMP_DAYS_UPPER_LIMIT: Int = 60

/**
 * Upper limit for generated timestamp hour values.
 */
const val TIMESTAMP_HOURS_UPPER_LIMIT: Int = 23

/**
 * Upper limit for generated timestamp minute values.
 */
const val TIMESTAMP_MINUTES_UPPER_LIMIT: Int = 59
