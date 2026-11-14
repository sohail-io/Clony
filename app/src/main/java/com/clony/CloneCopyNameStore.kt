package com.clony

import android.content.Context
import org.json.JSONObject

/** Keeps each copy's visible number stable for its lifetime and never reuses deleted numbers. */
internal object CloneCopyNameStore {
    private const val PREFERENCES = "clone_copy_names_v1"
    private const val ASSIGNMENTS = "assignments"
    private val packageCopyNumber = Regex("\\.clone([1-9][0-9]*)$")

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun assignmentKey(sourcePackageName: String, userId: Int) =
        "$sourcePackageName:$userId"

    private fun nextKey(sourcePackageName: String) = "next:$sourcePackageName"

    @Synchronized
    fun assign(
        context: Context,
        sourcePackageName: String,
        userId: Int,
        packageName: String? = null
    ): Int {
        val prefs = preferences(context)
        val assignments = JSONObject(prefs.getString(ASSIGNMENTS, "{}")!!)
        val key = assignmentKey(sourcePackageName, userId)
        assignments.optInt(key, 0).takeIf { it > 0 }?.let { existing ->
            val currentNext = prefs.getInt(nextKey(sourcePackageName), 1)
            val committed = prefs.edit()
                .putInt(nextKey(sourcePackageName), maxOf(currentNext, existing + 1))
                .commit()
            check(committed) { "Couldn't update the next copy number." }
            return existing
        }

        val usedNumbers = mutableSetOf<Int>()
        val prefix = "$sourcePackageName:"
        assignments.keys().forEach { assignmentKey ->
            if (assignmentKey.startsWith(prefix)) {
                assignments.optInt(assignmentKey, 0).takeIf { it > 0 }?.let(usedNumbers::add)
            }
        }
        val storedNext = prefs.getInt(nextKey(sourcePackageName), 1)
        val parsedNameNumber = packageName?.let { name ->
            packageCopyNumber.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        val nextFromAssignments = (usedNumbers.maxOrNull() ?: 0) + 1
        val firstAvailable = maxOf(storedNext, nextFromAssignments)
        val preferred = parsedNameNumber?.takeIf { it >= firstAvailable && it !in usedNumbers }
        var number = preferred ?: firstAvailable
        while (number in usedNumbers) number++

        assignments.put(key, number)
        val nextNumber = maxOf(firstAvailable, number + 1)
        check(prefs.edit()
            .putString(ASSIGNMENTS, assignments.toString())
            .putInt(nextKey(sourcePackageName), nextNumber)
            .commit()) { "Couldn't save the copy number." }
        return number
    }

    @Synchronized
    fun nextNumber(context: Context, sourcePackageName: String): Int {
        val prefs = preferences(context)
        val assignments = JSONObject(prefs.getString(ASSIGNMENTS, "{}")!!)
        val prefix = "$sourcePackageName:"
        var maximumAssigned = 0
        assignments.keys().forEach { assignmentKey ->
            if (assignmentKey.startsWith(prefix)) {
                maximumAssigned = maxOf(maximumAssigned, assignments.optInt(assignmentKey, 0))
            }
        }
        return maxOf(prefs.getInt(nextKey(sourcePackageName), 1), maximumAssigned + 1)
    }

    @Synchronized
    fun remove(context: Context, sourcePackageName: String, userId: Int): Boolean {
        val prefs = preferences(context)
        val assignments = JSONObject(prefs.getString(ASSIGNMENTS, "{}")!!)
        assignments.remove(assignmentKey(sourcePackageName, userId))
        // The next number is intentionally retained so deleting the newest copy never
        // causes a later clone to inherit its old visible name.
        return prefs.edit().putString(ASSIGNMENTS, assignments.toString()).commit()
    }
}
