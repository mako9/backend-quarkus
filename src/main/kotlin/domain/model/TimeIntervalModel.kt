package domain.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import utils.DateTimeUtils
import java.time.*
import java.time.temporal.TemporalAdjusters.nextOrSame

data class TimeIntervalModel @JsonCreator constructor(
    @JsonProperty("startDayOfWeek") private val startDayOfWeekInt: Int,
    @JsonProperty("endDayOfWeek") private val endDayOfWeekInt: Int,
    @JsonProperty("startTimeAtInMinute") private val startTimeAtInMinute: Int,
    @JsonProperty("endTimeAtInMinute") private val endTimeAtInMinute: Int
) {

    val startDayOfWeek: DayOfWeek
        get() = DayOfWeek.of(startDayOfWeekInt)

    val endDayOfWeek: DayOfWeek
        get() = DayOfWeek.of(endDayOfWeekInt)

    val startTime: OffsetTime
        get() = DateTimeUtils.getOffsetTimeOfMinutes(startTimeAtInMinute)

    val endTime: OffsetTime
        get() = DateTimeUtils.getOffsetTimeOfMinutes(endTimeAtInMinute)

    constructor(startDateTime: OffsetDateTime, endDateTime: OffsetDateTime) : this(
        startDayOfWeekInt = startDateTime.dayOfWeek.value,
        endDayOfWeekInt = endDateTime.dayOfWeek.value,
        startTimeAtInMinute = startDateTime.hour * 60 + startDateTime.minute,
        endTimeAtInMinute = endDateTime.hour * 60 + endDateTime.minute,
    )

    companion object {
        fun parseJsonString(json: String): List<TimeIntervalModel> {
            return try {
                val timeIntervalModels = ObjectMapper().readValue(json, Array<TimeIntervalModel>::class.java)
                    .toList()
                require(areTimeIntervalModelsValid(timeIntervalModels)) { "Invalid time intervals exist" }
                timeIntervalModels
            } catch (e: Exception) {
                println("Could not parse TimeInterval JSON: $e")
                emptyList()
            }
        }

        private fun areTimeIntervalModelsValid(timeIntervalModels: List<TimeIntervalModel>): Boolean {
            for ((index, timeIntervalModel) in timeIntervalModels.withIndex()) {
                val startLocalDateTime = getLocalDateTime(timeIntervalModel.startDayOfWeek, timeIntervalModel.startTime)
                val endLocalDateTime = getLocalDateTime(timeIntervalModel.endDayOfWeek, timeIntervalModel.endTime)
                val otherTimeIntervalModels = timeIntervalModels.filterIndexed { idx, _ -> idx != index }
                if (!timeIntervalModel.isValid() || otherTimeIntervalModels.any {
                        it.contains(startLocalDateTime) || it.contains(
                            endLocalDateTime
                        )
                    }) {
                    return false
                }
            }
            return true
        }

        private fun getLocalDateTime(dayOfWeek: DayOfWeek, localTime: OffsetTime): OffsetDateTime =
            OffsetDateTime.of(
                LocalDateTime.of(LocalDate.now(), localTime.toLocalTime()).with(nextOrSame(dayOfWeek)),
                ZoneOffset.UTC
            )
    }

    private fun isValid(): Boolean {
        return startDayOfWeek != endDayOfWeek || (startTime != endTime)
    }

    fun contains(dateTime: OffsetDateTime): Boolean {
        if (startDayOfWeek == endDayOfWeek) {
            return containsForSameDayIntervals(dateTime)
        }
        if (dateTime.dayOfWeek == startDayOfWeek && dateTime.toOffsetTime() < startTime) {
            return false
        }
        if (dateTime.dayOfWeek == endDayOfWeek && dateTime.toOffsetTime() > endTime) {
            return false
        }
        return dateTime.dayOfWeek >= startDayOfWeek && dateTime.dayOfWeek <= endDayOfWeek
    }

    private fun containsForSameDayIntervals(dateTime: OffsetDateTime): Boolean {
        if (startDayOfWeek != dateTime.dayOfWeek) {
            return false
        }
        if (dateTime.toOffsetTime() < startTime) {
            return false
        }
        return dateTime.toOffsetTime() <= endTime
    }
}

fun List<TimeIntervalModel>.containsDates(
    startLocalDateTime: OffsetDateTime,
    endLocalDateTime: OffsetDateTime
): Boolean =
    this.any { it.contains(startLocalDateTime) && it.contains(endLocalDateTime) }