package com.studentpay.manager.domain

/**
 * Payment type of a student (ported 1:1 from the legacy PWA, app.js: getTargetMonthIndex/getTargetYear).
 *
 * A = "Current Month":  a payment collected in month M is for month M.
 * B = "Previous Month": a payment collected in month M is for the immediately preceding calendar
 *                       month, M-1 (collected in January it is for the previous year's December).
 *                       Collected March 2026 -> February 2026; January 2026 -> December 2025;
 *                       December 2026 -> November 2026. See PaymentRules.
 */
enum class PaymentType(val code: String) {
    A("A"),
    B("B");

    companion object {
        /** Stable persistence code -> type. Returns null for anything that is not exactly "A" or "B". */
        fun fromCode(code: String?): PaymentType? = entries.firstOrNull { it.code == code }
    }
}

/** A calendar period. [month] is 0-based (0 = January ... 11 = December), exactly like the legacy data. */
data class Period(val year: Int, val month: Int) : Comparable<Period> {
    init {
        require(month in 0..11) { "month must be 0..11 but was $month" }
    }

    /** Months since year 0; handy for ordering and arithmetic. */
    val index: Int get() = year * 12 + month

    override fun compareTo(other: Period): Int = index.compareTo(other.index)

    /** Moves by [n] months (negative = back), carrying across years (Dec + 1 = Jan of next year). */
    fun plusMonths(n: Int): Period {
        val i = index + n
        return Period(Math.floorDiv(i, 12), Math.floorMod(i, 12))
    }
}

/**
 * Selectable ranges. Legacy Home/Payments year list: current year - 1 .. current year + 5.
 * Legacy report year list: current year - 2 .. current year + 1.
 */
object SelectionBounds {
    fun collection(today: Period): ClosedRange<Period> = Period(today.year - 1, 0)..Period(today.year + 5, 11)
    fun reports(today: Period): ClosedRange<Period> = Period(today.year - 2, 0)..Period(today.year + 1, 11)
}

data class Student(
    val id: Long,
    val name: String,
    val type: PaymentType,
    /** Whole Taka (the legacy app used parseInt). */
    val monthlyFee: Int,
    /**
     * When the student was deleted (UTC epoch millis), or null while active. "Deleting" a student only hides
     * them: their payments are kept, so the income already received is never lost. See StudentPayRepository.
     */
    val deletedAtMillis: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null
}

/**
 * One paid month for one student.
 * [year] + [monthPaidFor] identify the month the fee is FOR (legacy fields `year`, `month_paid_for`).
 * [paidOnMonth] is the month in which it was collected (legacy `paid_on_month`), null for very old records.
 * [paidAtMillis] is the payment date as UTC epoch millis (legacy `date`, an ISO string).
 * [amount] is the fee (whole Taka) that was charged WHEN the payment was recorded. It never changes
 * afterwards, so editing a student's monthly fee cannot alter past totals.
 */
data class Payment(
    val id: Long,
    val studentId: Long,
    val year: Int,
    val monthPaidFor: Int,
    val paidOnMonth: Int?,
    val paidAtMillis: Long,
    val amount: Int,
) {
    val period: Period get() = Period(year, monthPaidFor)
}

/** A deleted student with what they paid in total (shown on the "Deleted students" screen). */
data class DeletedStudentSummary(val student: Student, val totalReceived: Long, val paymentCount: Int)

object Months {
    val SHORT: List<String> =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val FULL: List<String> = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
