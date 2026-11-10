package com.clony

/** Slots are never recycled so an old launcher cannot point at a different clone. */
internal class LauncherRegistry(
    val assignments: MutableMap<String, Int> = linkedMapOf(),
    var nextSlot: Int = 1
) {
    fun assign(key: String): Int? {
        assignments[key]?.let { return it }
        if (nextSlot > LIMIT) return null
        return nextSlot++.also { assignments[key] = it }
    }

    fun remove(key: String) {
        assignments.remove(key)
    }

    fun resolve(slot: Int): String? = assignments.entries.firstOrNull { it.value == slot }?.key

    companion object {
        const val LIMIT = 100
        const val PREFIX = "com.clony.launcher.copy"

        fun packageName(slot: Int) = "$PREFIX$slot"

        fun slot(packageName: String): Int? {
            if (!packageName.startsWith(PREFIX)) return null
            val number = packageName.removePrefix(PREFIX).toIntOrNull() ?: return null
            return number.takeIf { it in 1..LIMIT && packageName(it) == packageName }
        }
    }
}
