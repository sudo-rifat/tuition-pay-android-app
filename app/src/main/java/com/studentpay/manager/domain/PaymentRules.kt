package com.studentpay.manager.domain

/**
 * The Type A / Type B rule. This is the single place where it lives.
 *
 * Source of truth: legacy app.js
 *   getTargetMonthIndex(type, clickedIndex): A -> clickedIndex; B -> clickedIndex-1, wrapping -1 to 11
 *   getTargetYear(type, clickedIndex, yr):   A -> yr;           B -> yr-1 when clickedIndex == 0, else yr
 *
 * "collection" month/year = what the user picks on the Payments/Home screens (when money is collected).
 * "target" = the month the fee is actually paid FOR (what is stored as year + month_paid_for).
 */
object PaymentRules {

    fun targetMonth(type: PaymentType, collectionMonth: Int): Int {
        require(collectionMonth in 0..11) { "collectionMonth must be 0..11" }
        return when (type) {
            PaymentType.A -> collectionMonth
            PaymentType.B -> if (collectionMonth == 0) 11 else collectionMonth - 1
        }
    }

    fun targetYear(type: PaymentType, collectionMonth: Int, collectionYear: Int): Int {
        require(collectionMonth in 0..11) { "collectionMonth must be 0..11" }
        return when (type) {
            PaymentType.A -> collectionYear
            PaymentType.B -> if (collectionMonth == 0) collectionYear - 1 else collectionYear
        }
    }

    fun targetPeriod(type: PaymentType, collectionMonth: Int, collectionYear: Int): Period =
        Period(
            year = targetYear(type, collectionMonth, collectionYear),
            month = targetMonth(type, collectionMonth),
        )
}
