package com.budget.app.data

import com.budget.app.data.remote.MonthPlanResponse
import kotlin.math.max
import kotlin.math.min

/**
 * The month's plan worked out again on the phone, for "Can we afford it?".
 * The same rules as the server (src/lib/month-plan.ts): the pool is money in
 * less bills and the debt payment; safe to spend is the pool less everything
 * spent and the essentials still to come; an extra's room is never more than
 * safe to spend. Pure, so it can be tested.
 */
object MonthPlanLogic {
    data class Group(val id: String, val tier: String, val manualCents: Long?, val usualCents: Long, val spentCents: Long)
    data class GroupOut(val id: String, val limitCents: Long?, val leftCents: Long?, val overCents: Long, val roomCents: Long?)
    data class Out(val poolCents: Long, val poolLeftCents: Long, val stillToComeCents: Long, val safeCents: Long, val groups: List<GroupOut>)

    fun plan(incomeCents: Long, billsCents: Long, debtCents: Long, groups: List<Group>, closed: Boolean = false): Out {
        val pool = incomeCents - billsCents - debtCents
        val limits = HashMap<String, Long>()
        var essentials = 0L
        groups.filter { it.tier == "ESSENTIAL" }.forEach { g ->
            val v = max(0L, g.manualCents ?: g.usualCents)
            limits[g.id] = v
            essentials += v
        }
        val flex = groups.filter { it.tier == "FLEXIBLE" }
        val set = flex.filter { it.manualCents != null }
        val auto = flex.filter { it.manualCents == null }
        val setTotal = set.sumOf { max(0L, it.manualCents!!) }
        set.forEach { limits[it.id] = max(0L, it.manualCents!!) }
        val autoUsual = auto.sumOf { max(0L, it.usualCents) }
        val roomForAuto = max(0L, pool - essentials - setTotal)
        val scale = if (autoUsual > 0) min(1.0, roomForAuto.toDouble() / autoUsual) else 0.0
        var given = 0L
        auto.forEachIndexed { i, g ->
            val usual = max(0L, g.usualCents)
            val v = when {
                scale >= 1.0 -> usual
                i == auto.lastIndex -> max(0L, min(usual, Math.floor(autoUsual * scale).toLong() - given))
                else -> Math.floor(usual * scale).toLong()
            }
            limits[g.id] = v
            given += v
        }
        val spent = groups.sumOf { it.spentCents }
        val poolLeft = pool - spent
        val toCome = if (closed) 0L else groups.filter { it.tier == "ESSENTIAL" }.sumOf { max(0L, (limits[it.id] ?: 0L) - it.spentCents) }
        val safe = poolLeft - toCome
        val out = groups.map { g ->
            if (g.tier != "ESSENTIAL" && g.tier != "FLEXIBLE") {
                GroupOut(g.id, null, null, 0, null)
            } else {
                val limit = limits[g.id] ?: 0L
                val left = max(0L, limit - g.spentCents)
                GroupOut(g.id, limit, left, max(0L, g.spentCents - limit), if (g.tier == "FLEXIBLE") max(0L, min(left, safe)) else null)
            }
        }
        return Out(pool, poolLeft, toCome, safe, out)
    }

    fun groupsOf(v: MonthPlanResponse): List<Group> =
        v.groups.map { Group(it.id, if (it.tier == "SORT") "UNPLANNED" else it.tier, it.manualCents, it.usualCents, it.spentCents) }

    /** The plan with some purchases added to their budgets' spending. */
    fun whatIf(v: MonthPlanResponse, purchases: List<Pair<String, Long>>): Out {
        val extra = purchases.groupBy({ it.first }, { max(0L, it.second) }).mapValues { it.value.sum() }
        val groups = groupsOf(v).map { it.copy(spentCents = it.spentCents + (extra[it.id] ?: 0L)) }
        return plan(v.income.totalCents, v.bills.totalCents, v.debt.cents, groups, v.closed)
    }
}
