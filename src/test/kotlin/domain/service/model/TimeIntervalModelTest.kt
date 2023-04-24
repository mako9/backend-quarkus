package domain.service.model

import domain.model.TimeIntervalModel
import domain.model.containsDates
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TimeIntervalModelTest {

    @Test
    fun `when parsing valid JSON string then a list of TimeIntervalModels is returned`() {
        val json = """
            [
                { 
                    "startDayOfWeek": 1,
                    "endDayOfWeek": 3,
                    "startTimeAtInMinute": 60,
                    "endTimeAtInMinute": 120
                }
            ]
        """.trimIndent()

        assertEquals(listOf(TimeIntervalModel(1, 3, 60, 120)), TimeIntervalModel.parseJsonString(json))
    }

    @Test
    fun `when parsing invalid JSON string then a empty list of TimeIntervalModels is returned`() {
        val json = """
            [
                { 
                    "invalid": 1,
                    "startTimeAtInMinute": 60,
                    "endTimeAtInMinute": 120
                }
            ]
        """.trimIndent()

        assertEquals(emptyList<TimeIntervalModel>(), TimeIntervalModel.parseJsonString(json))
    }

    @Test
    fun `when parsing JSON string containing invalid intervals then a list of TimeIntervalModels is returned`() {
        val json = """
            [
                { 
                    "startDayOfWeek": 1,
                    "endDayOfWeek": 3,
                    "startTimeAtInMinute": 60,
                    "endTimeAtInMinute": 120
                },
                { 
                    "startDayOfWeek": 2,
                    "endDayOfWeek": 2,
                    "startTimeAtInMinute": 60,
                    "endTimeAtInMinute": 120
                }
            ]
        """.trimIndent()

        assertEquals(emptyList<TimeIntervalModel>(), TimeIntervalModel.parseJsonString(json))
    }

    @Test
    fun `when checking if dates are in time interval then correct value is returned`() {
        var timeIntervalModels = listOf(
            TimeIntervalModel(1, 3, 60, 180)
        )

        run {
            assertTrue(
                timeIntervalModels.containsDates(
                    OffsetDateTime.of(2023, 4, 17, 1, 1, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2023, 4, 17, 1, 20, 0, 0, ZoneOffset.UTC),
                )
            )
        }

        run {
            assertFalse(
                timeIntervalModels.containsDates(
                    OffsetDateTime.of(2023, 4, 21, 1, 1, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2023, 4, 17, 1, 20, 0, 0, ZoneOffset.UTC),
                )
            )
        }

        run {
            timeIntervalModels = listOf(
                TimeIntervalModel(1, 1, 60, 180),
                TimeIntervalModel(3, 3, 60, 180),
            )

            assertFalse(
                timeIntervalModels.containsDates(
                    OffsetDateTime.of(2023, 4, 17, 1, 1, 0, 0, ZoneOffset.UTC),
                    OffsetDateTime.of(2023, 4, 18, 1, 20, 0, 0, ZoneOffset.UTC),
                )
            )
        }
    }
}