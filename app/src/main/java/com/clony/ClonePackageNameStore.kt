package com.clony

import android.content.Context

internal object ClonePackageNameStore {
    private const val PREFERENCES = "clone_package_names_v1"
    private const val MAX_LENGTH = 255
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun key(sourcePackageName: String, userId: Int) = "$sourcePackageName:$userId"

    fun read(context: Context, sourcePackageName: String, userId: Int): String? =
        preferences(context).getString(key(sourcePackageName, userId), null)

    fun isValid(packageName: String): Boolean {
        val value = packageName.trim()
        return value.length in 1..MAX_LENGTH && packagePattern.matches(value)
    }

    fun suggestedName(sourcePackageName: String, copyNumber: Int): String {
        val suffix = ".clone${copyNumber.coerceAtLeast(1)}"
        val candidate = "$sourcePackageName$suffix"
        if (candidate.length <= MAX_LENGTH && isValid(candidate)) return candidate

        val sourceHash = sourcePackageName.hashCode().toUInt().toString(16)
        return "com.clony.clone$sourceHash.copy${copyNumber.coerceAtLeast(1)}"
    }

    fun write(context: Context, sourcePackageName: String, userId: Int, packageName: String): Boolean {
        val value = packageName.trim()
        if (!isValid(value)) return false
        return preferences(context).edit()
            .putString(key(sourcePackageName, userId), value)
            .commit()
    }

    fun delete(context: Context, sourcePackageName: String, userId: Int): Boolean =
        preferences(context).edit()
            .remove(key(sourcePackageName, userId))
            .commit()
}
