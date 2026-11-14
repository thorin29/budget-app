package com.budget.app.data

/** Light, dark, or whatever the phone is set to. Chosen per phone under More → Appearance. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Follow phone"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** The one accent color. Cobalt unless the person picks another. */
enum class Accent(val key: String, val label: String) {
    COBALT("cobalt", "Cobalt"),
    VIOLET("violet", "Violet"),
    GRAPHITE("graphite", "Graphite"),
    FOREST("forest", "Forest"),
    PLUM("plum", "Plum"),
    TEAL("teal", "Teal");

    companion object {
        fun from(key: String?): Accent = entries.firstOrNull { it.key == key } ?: COBALT
    }
}

data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Accent.COBALT)
