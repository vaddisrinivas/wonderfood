package com.wonderfood.app.testing

import java.nio.charset.StandardCharsets
import java.util.UUID

class DeterministicLongIdSource(start: Long = 1L) {
    private var nextValue = start

    fun nextId(): Long = nextValue++

    fun reset(start: Long = 1L) {
        nextValue = start
    }
}

class DeterministicUuidSource(start: Long = 1L) {
    private var nextLeastSignificantBits = start

    fun nextUuid(): UUID = UUID(WONDERFOOD_TEST_UUID_MSB, nextLeastSignificantBits++)

    fun nextString(): String = nextUuid().toString()

    fun uuidFor(label: String): UUID =
        UUID.nameUUIDFromBytes("wonderfood-test:$label".toByteArray(StandardCharsets.UTF_8))

    fun reset(start: Long = 1L) {
        nextLeastSignificantBits = start
    }

    private companion object {
        const val WONDERFOOD_TEST_UUID_MSB: Long = 0x000000000000D002L
    }
}
