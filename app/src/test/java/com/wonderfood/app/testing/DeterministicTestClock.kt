package com.wonderfood.app.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DeterministicTestClock(
    private var currentInstant: Instant = DEFAULT_TEST_INSTANT,
    private val currentZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = DeterministicTestClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun set(instant: Instant) {
        currentInstant = instant
    }

    fun advanceBy(duration: Duration): Instant {
        currentInstant = currentInstant.plus(duration)
        return currentInstant
    }

    companion object {
        val DEFAULT_TEST_INSTANT: Instant = Instant.parse("2026-01-15T12:00:00Z")
    }
}
