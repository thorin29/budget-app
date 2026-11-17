package com.budget.app.data

import com.budget.app.data.remote.EnvelopeDto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The sharing rule behind "Can I afford it?", the same as the server's: no
 * envelope goes negative, and an overage comes out of the flexible envelopes
 * that still have room, each giving in proportion to its room. A save-up's
 * balance (and any envelope that carries money) doesn't give.
 */
object EnvelopeLogic {
    data class Row(val id: String, val name: String, val leftCents: Long, val overCents: Long, val gaveCents: Long)
    data class Result(val rows: List<Row>, val flexibleLeftCents: Long, val shortCents: Long)

    fun share(envelopes: List<EnvelopeDto>, extraFor: String? = null, extraCents: Long = 0): Result {
        val rooms = envelopes.map { e ->
            val spent = e.spentCents + if (e.id == extraFor) max(0, extraCents) else 0
            e.limitCents + (e.carriedCents ?: 0) - spent
        }
        val need = rooms.sumOf { max(0, -it) }
        val givers = envelopes.indices.filter { envelopes[it].tier == "FLEXIBLE" && envelopes[it].carriedCents == null && rooms[it] > 0 }
        val room = givers.sumOf { rooms[it] }
        val taken = min(need, room)
        val gave = LongArray(envelopes.size)
        if (taken > 0) {
            var handed = 0L
            givers.forEachIndexed { n, i ->
                val part = if (n == givers.lastIndex) taken - handed else min(rooms[i], (taken.toDouble() * rooms[i] / room).roundToLong())
                gave[i] = part
                handed += part
            }
        }
        val rows = envelopes.mapIndexed { i, e -> Row(e.id, e.name, max(0, rooms[i]) - gave[i], max(0, -rooms[i]), gave[i]) }
        return Result(rows, room - need, need - taken)
    }

    /** "$1,234" from "1234", "1,234.50" or "$1234"; null when it isn't an amount above zero. */
    fun parseDollars(text: String): Long? {
        val n = text.replace("$", "").replace(",", "").trim().toDoubleOrNull() ?: return null
        return if (n > 0) (n * 100).roundToLong() else null
    }
}
