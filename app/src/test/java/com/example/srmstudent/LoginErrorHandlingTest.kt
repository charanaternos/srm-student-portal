package com.example.srmstudent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorHandlingTest {

    @Test
    fun slotToTiming_mapsStandardSlotsToProperTimes() {
        assertTrue(slotToTiming("A1").contains("08:00 AM - 08:50 AM"))
        assertTrue(slotToTiming("B2").contains("08:50 AM - 09:40 AM"))
        assertTrue(slotToTiming("C1").contains("09:45 AM - 10:35 AM"))
        assertTrue(slotToTiming("D").contains("10:40 AM - 11:30 AM"))
        assertTrue(slotToTiming("E1").contains("11:35 AM - 12:25 PM"))
        assertTrue(slotToTiming("F2").contains("12:30 PM - 01:20 PM"))
        assertTrue(slotToTiming("G").contains("01:25 PM - 02:15 PM"))
        assertTrue(slotToTiming("P1").contains("Lab Session"))
        assertTrue(slotToTiming("L1").contains("Lab Session"))
        assertEquals("Slot X9", slotToTiming("X9"))
        assertEquals("Scheduled", slotToTiming(""))
    }

    @Test
    fun attendanceItem_percentageCalculation_isAccurate() {
        // Explicit percentage given
        val itemExplicit = AttendanceItem(
            subject = "Algorithms",
            code = "CSC204",
            attended = 20,
            total = 25,
            rawPercentage = 80.0
        )
        assertEquals(80, itemExplicit.percentage)

        // Computed percentage without rawPercentage
        val itemComputed = AttendanceItem(
            subject = "Data Structures",
            code = "CSC201",
            attended = 18,
            total = 20,
            rawPercentage = null
        )
        assertEquals(90, itemComputed.percentage)

        // Zero total safe handling
        val itemZero = AttendanceItem(
            subject = "Seminar",
            code = "SEM101",
            attended = 0,
            total = 0,
            rawPercentage = null
        )
        assertEquals(0, itemZero.percentage)
    }

    @Test
    fun markItem_percentageCalculation_isAccurate() {
        val markItem = MarkItem(
            subject = "Mathematics",
            code = "MAT101",
            assessment = "Cycle Test 1",
            obtained = 45.0,
            total = 50.0,
            rawScoreText = "45 / 50"
        )
        assertEquals(90, markItem.percentage)

        val zeroTotalMark = MarkItem(
            subject = "Lab",
            code = "LAB101",
            assessment = "Viva",
            obtained = 0.0,
            total = 0.0,
            rawScoreText = "Ungraded"
        )
        assertEquals(0, zeroTotalMark.percentage)
    }

    @Test
    fun portalSnapshotUiData_verifiesCompleteDataStructure() {
        val snapshot = PortalSnapshotUiData(
            studentName = "Charan Vadla",
            regNumber = "RA2311003010999",
            department = "CSE Core",
            semester = "4",
            dayOrder = "2",
            syncedAt = "07 Nov, 08:30 PM",
            classes = listOf(
                ClassItem(
                    subject = "Design and Analysis of Algorithms",
                    code = "21CSC204J",
                    day = "DO-2",
                    date = "07 NOV",
                    time = "08:00 AM - 08:50 AM (Slot A1)",
                    room = "TP-402",
                    faculty = "Dr. K. Sharma"
                )
            ),
            attendance = listOf(
                AttendanceItem(
                    subject = "Design and Analysis of Algorithms",
                    code = "21CSC204J",
                    attended = 27,
                    total = 30,
                    rawPercentage = 90.0
                )
            ),
            marks = listOf(
                MarkItem(
                    subject = "Design and Analysis of Algorithms",
                    code = "21CSC204J",
                    assessment = "Cycle Test 1",
                    obtained = 23.5,
                    total = 25.0,
                    rawScoreText = "23.5 / 25"
                )
            )
        )

        assertEquals("Charan Vadla", snapshot.studentName)
        assertEquals("RA2311003010999", snapshot.regNumber)
        assertEquals("CSE Core", snapshot.department)
        assertEquals("4", snapshot.semester)
        assertEquals("2", snapshot.dayOrder)
        assertEquals(1, snapshot.classes.size)
        assertEquals(1, snapshot.attendance.size)
        assertEquals(1, snapshot.marks.size)
        assertEquals(90, snapshot.attendance[0].percentage)
        assertEquals(94, snapshot.marks[0].percentage)
    }
}
