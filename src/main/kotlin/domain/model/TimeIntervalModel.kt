package domain.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import utils.DateTimeUtils
import java.time.*
import java.time.temporal.TemporalAdjusters.nextOrSame

data class TimeIntervalModel @JsonCreator constructor(
    @JsonProperty("startDayOfWeek") val startDayOfWeekInt: Int,
    @JsonProperty("endDayOfWeek") val endDayOfWeekInt: Int,
    @JsonProperty("startTimeAtInMinute") val startTimeAtInMinute: Int,
    @JsonProperty("endTimeAtInMinute") val endTimeAtInMinute: Int
) {

    @get:JsonIgnore
    val startDayOfWeek: DayOfWeek
        get() = DayOfWeek.of(startDayOfWeekInt)

    @get:JsonIgnore
    val endDayOfWeek: DayOfWeek
        get() = DayOfWeek.of(endDayOfWeekInt)

    @get:JsonIgnore
    val startTime: OffsetTime
        get() = DateTimeUtils.getOffsetTimeOfMinutes(startTimeAtInMinute)

    @get:JsonIgnore
    val endTime: OffsetTime
        get() = DateTimeUtils.getOffsetTimeOfMinutes(endTimeAtInMinute)

    @JsonIgnore
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
                val startDateTime = getOffsetDateTime(timeIntervalModel.startDayOfWeek, timeIntervalModel.startTime)
                val endDateTime = getOffsetDateTime(timeIntervalModel.endDayOfWeek, timeIntervalModel.endTime)
                val otherTimeIntervalModels = timeIntervalModels.filterIndexed { idx, _ -> idx != index }
                if (!timeIntervalModel.isValid() || otherTimeIntervalModels.any {
                        it.contains(startDateTime) || it.contains(
                            endDateTime
                        )
                    }) {
                    return false
                }
            }
            return true
        }

        private fun getOffsetDateTime(dayOfWeek: DayOfWeek, time: OffsetTime): OffsetDateTime =
            OffsetDateTime.of(
                LocalDateTime.of(LocalDate.now(), time.toLocalTime()).with(nextOrSame(dayOfWeek)),
                time.offset
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
    startDateTime: OffsetDateTime,
    endDateTime: OffsetDateTime
): Boolean =
    this.any { it.contains(startDateTime) && it.contains(endDateTime) }