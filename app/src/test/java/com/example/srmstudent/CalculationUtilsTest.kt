package com.example.srmstudent

import com.example.srmstudent.domain.util.CalculationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationUtilsTest {

    @Test
    fun calculatePercentage_standardValues_returnsCorrectIntegerPercentage() {
        val percent1 = CalculationUtils.calculatePercentage(obtained = 26, total = 30)
        assertEquals(86, percent1)

        val percent2 = CalculationUtils.calculatePercentage(obtained = 15, total = 20)
        assertEquals(75, percent2)

        val percent3 = CalculationUtils.calculatePercentage(obtained = 50, total = 50)
        assertEquals(100, percent3)

        val percent4 = CalculationUtils.calculatePercentage(obtained = 0, total = 30)
        assertEquals(0, percent4)
    }

    @Test
    fun calculatePercentage_zeroTotal_returnsZeroWithoutException() {
        val result = CalculationUtils.calculatePercentage(obtained = 10, total = 0)
        assertEquals(0, result)
    }

    @Test
    fun calculatePercentage_negativeInputs_returnsZeroSafely() {
        val resultNegativeObtained = CalculationUtils.calculatePercentage(obtained = -5, total = 20)
        assertEquals(0, resultNegativeObtained)

        val resultNegativeTotal = CalculationUtils.calculatePercentage(obtained = 10, total = -10)
        assertEquals(0, resultNegativeTotal)
    }

    @Test
    fun calculatePercentage_obtainedExceedingTotal_capsAt100() {
        val result = CalculationUtils.calculatePercentage(obtained = 120, total = 100)
        assertEquals(100, result)
    }

    @Test
    fun isAttendanceSufficient_evaluatesAgainstDefault75PercentThreshold() {
        assertTrue("75% should be sufficient", CalculationUtils.isAttendanceSufficient(75))
        assertTrue("85% should be sufficient", CalculationUtils.isAttendanceSufficient(85))
        assertTrue("100% should be sufficient", CalculationUtils.isAttendanceSufficient(100))

        assertFalse("74% should be insufficient (shortage)", CalculationUtils.isAttendanceSufficient(74))
        assertFalse("0% should be insufficient", CalculationUtils.isAttendanceSufficient(0))
        assertFalse("50% should be insufficient", CalculationUtils.isAttendanceSufficient(50))
    }

    @Test
    fun isAttendanceSufficient_customThreshold_evaluatesCorrectly() {
        assertTrue(CalculationUtils.isAttendanceSufficient(percentage = 80, threshold = 80))
        assertFalse(CalculationUtils.isAttendanceSufficient(percentage = 79, threshold = 80))
    }
}

