package com.studentpay.manager.domain

enum class NameError { EMPTY, TOO_LONG }
enum class FeeError { EMPTY, NOT_A_NUMBER, TOO_LARGE }

/** Result of validating the student form. Exactly one of (value, error) is set per field. */
data class StudentFormResult(
    val name: String?,
    val fee: Int?,
    val nameError: NameError?,
    val feeError: FeeError?,
) {
    val isValid: Boolean get() = nameError == null && feeError == null
}

/**
 * Student input rules. Legacy: name was a required text input, fee a required integer >= 0
 * (parseInt(...) || 0). We additionally trim the name and cap lengths so records stay sane.
 */
object StudentValidator {
    const val MAX_NAME_LENGTH = 80
    const val MAX_FEE = 10_000_000

    fun validate(rawName: String, rawFee: String): StudentFormResult {
        val name = rawName.trim()
        val nameError = when {
            name.isEmpty() -> NameError.EMPTY
            name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            else -> null
        }

        val feeText = rawFee.trim()
        var fee: Int? = null
        val feeError: FeeError? = when {
            feeText.isEmpty() -> FeeError.EMPTY
            // ASCII digits only: Char.isDigit() would also accept e.g. Bengali digits.
            !feeText.all { it in '0'..'9' } -> FeeError.NOT_A_NUMBER
            feeText.trimStart('0').length > 8 -> FeeError.TOO_LARGE
            else -> {
                val v = feeText.toLong()
                if (v > MAX_FEE) {
                    FeeError.TOO_LARGE
                } else {
                    fee = v.toInt()
                    null
                }
            }
        }
        return StudentFormResult(
            name = if (nameError == null) name else null,
            fee = fee,
            nameError = nameError,
            feeError = feeError,
        )
    }
}
