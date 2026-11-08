package com.example.srmstudent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorHandlingTest {

    @Test
    fun slotTime_mapsStandardSlotsToProperTimes() {
        assertTrue(slotTime("A1").contains("08:00 - 08:50 AM"))
        assertTrue(slotTime("B2").contains("08:50 - 09:40 AM"))
        assertTrue(slotTime("C1").contains("09:45 - 10:35 AM"))
        assertTrue(slotTime("D").contains("10:40 - 11:30 AM"))
        assertTrue(slotTime("E1").contains("11:35 - 12:25 PM"))
        assertTrue(slotTime("F2").contains("12:30 - 01:20 PM"))
        assertTrue(slotTime("G").contains("01:25 - 02:15 PM"))
        assertTrue(slotTime("P1").contains("Lab"))
        assertTrue(slotTime("L1").contains("Lab"))
        assertEquals("Slot X9", slotTime("X9"))
        assertEquals("Scheduled", slotTime(""))
    }

    @Test
    fun attendanceItem_percentageCalculation_isAccurate() {
        // Explicit percentage given
        val itemExplicit = AttendanceItem(
            subject = "Algorithms",
            code = "CSC204",
            attended = 20,
            total = 25,
            rawPct = 80.0
        )
        assertEquals(80, itemExplicit.pct)

        // Computed percentage without rawPct
        val itemComputed = AttendanceItem(
            subject = "Data Structures",
            code = "CSC201",
            attended = 18,
            total = 20,
            rawPct = null
        )
        assertEquals(90, itemComputed.pct)

        // Zero total safe handling
        val itemZero = AttendanceItem(
            subject = "Seminar",
            code = "SEM101",
            attended = 0,
            total = 0,
            rawPct = null
        )
        assertEquals(0, itemZero.pct)
    }

    @Test
    fun markItem_percentageCalculation_isAccurate() {
        val markItem = MarkItem(
            subject = "Mathematics",
            code = "MAT101",
            assessment = "Cycle Test 1",
            obtained = 45.0,
            total = 50.0,
            raw = "45 / 50"
        )
        assertEquals(90, markItem.pct)

        val zeroTotalMark = MarkItem(
            subject = "Lab",
            code = "LAB101",
            assessment = "Viva",
            obtained = 0.0,
            total = 0.0,
            raw = "Ungraded"
        )
        assertEquals(0, zeroTotalMark.pct)
    }

    @Test
    fun portalData_verifiesCompleteDataStructure() {
        val snapshot = PortalData(
            name = "Charan Vadla",
            regNo = "RA2311003010999",
            dept = "CSE Core",
            sem = "4",
            dayOrder = "2",
            syncedAt = "08 Nov, 10:30 AM",
            classes = listOf(
                ClassItem(
                    subject = "Design and Analysis of Algorithms",
                    code = "21CSC204J",
                    day = "DO-2",
                    date = "08 NOV",
                    time = "08:00 - 08:50 AM (A1)",
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
                    rawPct = 90.0
                )
            ),
            marks = listOf(
                MarkItem(
                    subject = "Design and Analysis of Algorithms",
                    code = "21CSC204J",
                    assessment = "Cycle Test 1",
                    obtained = 23.5,
                    total = 25.0,
                    raw = "23.5 / 25"
                )
            )
        )

        assertEquals("Charan Vadla", snapshot.name)
        assertEquals("RA2311003010999", snapshot.regNo)
        assertEquals("CSE Core", snapshot.dept)
        assertEquals("4", snapshot.sem)
        assertEquals("2", snapshot.dayOrder)
        assertEquals(1, snapshot.classes.size)
        assertEquals(1, snapshot.attendance.size)
        assertEquals(1, snapshot.marks.size)
        assertEquals(90, snapshot.attendance[0].pct)
        assertEquals(94, snapshot.marks[0].pct)
    }
}
