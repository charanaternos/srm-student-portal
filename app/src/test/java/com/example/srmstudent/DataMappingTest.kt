package com.example.srmstudent

import com.example.srmstudent.data.local.entity.ClassEntity
import com.example.srmstudent.data.model.AttendanceDto
import com.example.srmstudent.data.model.ClassDto
import com.example.srmstudent.data.model.MarkDto
import com.example.srmstudent.data.model.PortalSnapshotDto
import com.example.srmstudent.data.model.SnapshotPayloadDto
import com.example.srmstudent.domain.util.CalculationUtils
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataMappingTest {

    private val gson = Gson()

    @Test
    fun classDto_jsonDeserializationAndMapping_preservesAllFields() {
        val json = """
            {
                "subject": "Data Structures & Algorithms",
                "course_code": "21CSC204J",
                "day_order": "1",
                "day": "MON",
                "date": "24 AUG",
                "start_time": "08:45",
                "end_time": "09:35",
                "room": "TP-402",
                "faculty": "Dr. Ramesh V.",
                "details": "DSA details"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, ClassDto::class.java)
        assertEquals("Data Structures & Algorithms", dto.subject)
        assertEquals("21CSC204J", dto.courseCode)
        assertEquals("1", dto.dayOrder)
        assertEquals("MON", dto.day)
        assertEquals("08:45", dto.startTime)
        assertEquals("09:35", dto.endTime)
        assertEquals("TP-402", dto.room)
        assertEquals("Dr. Ramesh V.", dto.faculty)

        val entity = ClassEntity(
            id = "1_21CSC204J_08:45_0",
            subject = dto.subject ?: "",
            code = dto.courseCode ?: "",
            dayOrder = dto.dayOrder ?: "",
            day = dto.day ?: "",
            date = dto.date ?: "",
            startTime = dto.startTime ?: "",
            endTime = dto.endTime ?: "",
            timeSlot = "${dto.startTime} – ${dto.endTime}",
            room = dto.room ?: "",
            faculty = dto.faculty ?: "",
            details = dto.details ?: ""
        )

        val domain = entity.toDomain()
        assertEquals(entity.id, domain.id)
        assertEquals("Data Structures & Algorithms", domain.subject)
        assertEquals("21CSC204J", domain.code)
        assertEquals("1", domain.dayOrder)
        assertEquals("TP-402", domain.room)
        assertEquals("Dr. Ramesh V.", domain.faculty)
    }

    @Test
    fun attendanceDto_stringAndNumericParsing_calculatesSafely() {
        val jsonStringValues = """
            {
                "subject": "Computer Networks",
                "course_code": "21CSC301J",
                "attended": "25",
                "total": "34"
            }
        """.trimIndent()

        val dto = gson.fromJson(jsonStringValues, AttendanceDto::class.java)
        assertEquals(25, dto.parseAttended())
        assertEquals(34, dto.parseTotal())

        val percent = CalculationUtils.calculatePercentage(dto.parseAttended(), dto.parseTotal())
        assertEquals(73, percent)

        val isShortage = !CalculationUtils.isAttendanceSufficient(percent, 75)
        assertTrue("73% should trigger shortage warning", isShortage)
    }

    @Test
    fun markDto_parsingAndPercentageCalculation_isAccurate() {
        val json = """
            {
                "subject": "Operating Systems",
                "course_code": "21CSC206J",
                "assessment": "Cycle Test 1",
                "obtained": 46,
                "total": 50,
                "date": "16 SEP"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, MarkDto::class.java)
        assertEquals(46, dto.parseObtained())
        assertEquals(50, dto.parseTotal())

        val percent = CalculationUtils.calculatePercentage(dto.parseObtained(), dto.parseTotal())
        assertEquals(92, percent)
    }

    @Test
    fun portalSnapshotDto_resolvesEmbeddedOrDirectPayloads() {
        val embeddedJson = """
            {
                "snapshot_date": "2026-11-07",
                "data": {
                    "classes": [{"subject": "Math", "course_code": "MAT101"}],
                    "attendance": [{"subject": "Math", "attended": 20, "total": 20}],
                    "marks": [{"subject": "Math", "assessment": "Quiz", "obtained": 10, "total": 10}]
                }
            }
        """.trimIndent()

        val embeddedSnapshot = gson.fromJson(embeddedJson, PortalSnapshotDto::class.java)
        assertEquals(1, embeddedSnapshot.getResolvedClasses().size)
        assertEquals(1, embeddedSnapshot.getResolvedAttendance().size)
        assertEquals(1, embeddedSnapshot.getResolvedMarks().size)

        val directJson = """
            {
                "classes": [{"subject": "Physics", "course_code": "PHY101"}],
                "attendance": [{"subject": "Physics", "attended": 18, "total": 20}]
            }
        """.trimIndent()

        val directSnapshot = gson.fromJson(directJson, PortalSnapshotDto::class.java)
        assertEquals(1, directSnapshot.getResolvedClasses().size)
        assertEquals("Physics", directSnapshot.getResolvedClasses()[0].subject)
        assertEquals(1, directSnapshot.getResolvedAttendance().size)
        assertEquals(0, directSnapshot.getResolvedMarks().size)
    }

    @Test
    fun studentProfile_computedPropertiesAndFallbacks() {
        val profile = com.example.srmstudent.domain.model.StudentProfile(
            displayName = "Alex Morgan",
            studentId = "RA2311003010999",
            programme = "B.Tech CSE",
            semester = "Semester 4"
        )

        assertEquals("Alex Morgan", profile.name)
        assertEquals("RA2311003010999", profile.regNumber)
        assertEquals("B.Tech CSE", profile.program)

        val fallbackProfile = com.example.srmstudent.domain.model.StudentProfile(
            displayName = null,
            studentId = "RA2311003010888"
        )
        assertEquals("RA2311003010888", fallbackProfile.name)
        assertEquals("RA2311003010888", fallbackProfile.regNumber)
        assertEquals("", fallbackProfile.program)
    }
}

