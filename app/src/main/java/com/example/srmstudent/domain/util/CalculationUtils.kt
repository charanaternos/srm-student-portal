package com.example.srmstudent.domain.util

object CalculationUtils {
    /**
     * Calculates percentage safely without dividing by zero or producing negative values.
     */
    fun calculatePercentage(obtained: Int, total: Int): Int {
        if (total <= 0 || obtained < 0) return 0
        val percent = (obtained.toLong() * 100 / total).toInt()
        return percent.coerceIn(0, 100)
    }

    /**
     * Checks if attendance meets or exceeds the SRM threshold (default 75%).
     */
    fun isAttendanceSufficient(percentage: Int, threshold: Int = 75): Boolean {
        return percentage >= threshold
    }
}

