package com.kunvarpreet.ascend.domain.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure Kotlin abstraction over system clock and timezone providers.
 * Decouples time calculations from Android framework and device clock for
 * deterministic unit testing of midnight flips, streak catch-up, and timezone changes.
 */
interface AscendClock {
    fun nowMillis(): Long
    fun todayIsoDate(): String
    fun zoneId(): ZoneId
}

/**
 * Default production clock backed by Java time APIs.
 */
class SystemAscendClock(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : AscendClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayIsoDate(): String = LocalDate.now(zoneId).toString()
    override fun zoneId(): ZoneId = zoneId
}

/**
 * Deterministic test clock supporting manual time advancement and timezone shifting.
 */
class FakeAscendClock(
    private var currentMillis: Long = Instant.parse("2026-11-11T12:00:00Z").toEpochMilli(),
    private var zoneId: ZoneId = ZoneId.of("UTC")
) : AscendClock {
    override fun nowMillis(): Long = currentMillis

    override fun todayIsoDate(): String =
        Instant.ofEpochMilli(currentMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toString()

    override fun zoneId(): ZoneId = zoneId

    fun setTime(millis: Long) {
        currentMillis = millis
    }

    fun setIsoDate(isoDate: String) {
        val date = LocalDate.parse(isoDate)
        currentMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun setZoneId(newZoneId: ZoneId) {
        zoneId = newZoneId
    }

    fun advanceMillis(millis: Long) {
        currentMillis += millis
    }

    fun advanceMinutes(minutes: Long) {
        advanceMillis(minutes * 60_000L)
    }

    fun advanceHours(hours: Long) {
        advanceMillis(hours * 3_600_000L)
    }

    fun advanceDays(days: Long) {
        advanceMillis(days * 86_400_000L)
    }
}
