package com.budget.app.data

/** Which glyph a line item gets. The UI maps each to a Phosphor icon. */
enum class BillIconKey {
    HOME, ELECTRIC, WATER, GAS_HEAT, TRASH, INTERNET, PHONE, FUEL, CAR, INSURANCE, GYM,
    STREAMING, SCHOOL, HEALTH, GROCERIES, PET, CARD, INCOME, SAVINGS, OTHER,
}

/**
 * Picks an icon from the item's name, then its category. Line items have no
 * icon of their own on the server, so this is a best guess; anything it can't
 * place gets a receipt. Pure, so it is unit-tested.
 */
object BillIcons {
    // Order matters: "car insurance" is insurance, not car.
    private val rules: List<Pair<BillIconKey, List<String>>> = listOf(
        BillIconKey.INCOME to listOf("paycheck", "salary", "payroll", "income", "wage", "bonus"),
        BillIconKey.SAVINGS to listOf("saving", "investment", "invest", "401k", "ira", "brokerage", "emergency fund"),
        BillIconKey.INSURANCE to listOf("insurance", "policy", "premium"),
        BillIconKey.ELECTRIC to listOf("electric", "electricity", "power", "energy"),
        BillIconKey.WATER to listOf("water", "sewer"),
        BillIconKey.GAS_HEAT to listOf("natural gas", "gas bill", "heating", "propane"),
        BillIconKey.TRASH to listOf("trash", "garbage", "waste", "recycling"),
        BillIconKey.INTERNET to listOf("internet", "wifi", "broadband", "fiber", "cable"),
        BillIconKey.PHONE to listOf("phone", "mobile", "cell", "wireless"),
        BillIconKey.FUEL to listOf("fuel", "gasoline"),
        BillIconKey.CAR to listOf("car", "auto", "vehicle", "truck", "toll", "registration"),
        BillIconKey.HOME to listOf("mortgage", "rent", "hoa", "home", "house", "property tax"),
        BillIconKey.GYM to listOf("gym", "fitness", "yoga"),
        BillIconKey.STREAMING to listOf("netflix", "hulu", "spotify", "streaming", "stream", "disney", "youtube", "subscription", "tv"),
        BillIconKey.SCHOOL to listOf("school", "tuition", "student", "college", "daycare"),
        BillIconKey.HEALTH to listOf("doctor", "medical", "dental", "health", "pharmacy", "hospital"),
        BillIconKey.GROCERIES to listOf("grocery", "groceries", "food"),
        BillIconKey.PET to listOf("pet", "vet", "dog", "cat"),
        BillIconKey.CARD to listOf("credit card", "card", "visa", "amex", "mastercard", "discover", "loan"),
    )

    private val compiled = rules.map { (key, words) ->
        key to words.map { Regex("\\b" + Regex.escape(it) + "s?\\b") }
    }

    fun forItem(name: String, category: String?, isIncome: Boolean): BillIconKey {
        if (isIncome) return BillIconKey.INCOME
        match(name)?.let { return it }
        category?.let { match(it) }?.let { return it }
        return BillIconKey.OTHER
    }

    private fun match(text: String): BillIconKey? {
        val t = text.lowercase()
        return compiled.firstOrNull { (_, patterns) -> patterns.any { it.containsMatchIn(t) } }?.first
    }
}
