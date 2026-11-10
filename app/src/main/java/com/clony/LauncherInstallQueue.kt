package com.clony

internal class LauncherInstallQueue(
    val pending: ArrayList<String> = arrayListOf(),
    var active: String? = null
) {
    fun add(packages: List<String>) {
        packages.forEach { if (it != active && it !in pending) pending.add(it) }
    }

    fun startNext(isInstalled: (String) -> Boolean): String? {
        if (active != null) return null
        while (pending.isNotEmpty()) {
            val candidate = pending.removeAt(0)
            if (!isInstalled(candidate)) {
                active = candidate
                return candidate
            }
        }
        return null
    }

    fun complete(success: Boolean) {
        active = null
        if (!success) pending.clear()
    }
}
