package com.clony

import android.content.Context

/** Stores the user's reCAPTCHA compatibility preference for new and existing copies. */
internal object CloneRecaptchaWorkaroundStore {
    private const val PREFERENCES = "clone_recaptcha_workaround_v1"
    private const val KEY_PREFIX = "recaptcha_workaround:"

    fun readTemplate(context: Context, packageName: String): Boolean {
        if (!isValid(packageName)) return false
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(templateKey(packageName), false)
    }

    fun read(context: Context, packageName: String, userId: Int): Boolean {
        if (!isValid(packageName) || userId < 0) return false
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return preferences.getBoolean(cloneKey(packageName, userId), readTemplate(context, packageName))
    }

    fun writeTemplate(context: Context, packageName: String, enabled: Boolean): Boolean {
        if (!isValid(packageName)) return false
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(templateKey(packageName), enabled).commit()
    }

    fun write(context: Context, packageName: String, userId: Int, enabled: Boolean): Boolean {
        if (!isValid(packageName) || userId < 0) return false
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(cloneKey(packageName, userId), enabled).commit()
    }

    fun delete(context: Context, packageName: String, userId: Int): Boolean {
        if (!isValid(packageName) || userId < 0) return false
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(cloneKey(packageName, userId)).commit()
    }

    private fun templateKey(packageName: String) = "${KEY_PREFIX}template:$packageName"
    private fun cloneKey(packageName: String, userId: Int) = "${KEY_PREFIX}clone:$packageName:$userId"
    private fun isValid(packageName: String) = packageName.matches(Regex("[A-Za-z0-9_.]+"))
}
