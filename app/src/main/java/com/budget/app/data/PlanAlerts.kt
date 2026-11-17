package com.budget.app.data

import com.budget.app.data.remote.EnvelopesResponse

/** The words of the spending plan alerts. Pure, so it can be tested. */
object PlanAlerts {
    /** Payday: the debt payment, bills to hold, what goes to the cards. Null without a plan. */
    fun payday(v: EnvelopesResponse): Pair<String, String>? {
        val p = v.payday ?: return null
        val sent = p.cards.sumOf { it.sendCents }
        val zero = p.cards.filter { it.owedCents > 0 }.all { it.result == "zero" }
        val parts = listOfNotNull(
            if (p.debtCents > 0) "${Money.whole(p.debtCents)} to ${p.debtName ?: "the 0% card"}" else null,
            "hold ${Money.whole(p.holdCents)} for bills",
            if (sent > 0) "${Money.whole(sent)} to the cards" + if (zero) " (pays them to zero)" else "" else null,
        )
        val title = if (p.forCardsCents < 0) "Payday: checking is short ${Money.whole(-p.forCardsCents)}" else "Payday plan"
        return title to parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ". ${Money.whole(p.afterCents)} stays in checking."
    }
}
