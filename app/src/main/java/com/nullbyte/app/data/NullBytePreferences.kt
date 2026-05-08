package com.nullbyte.app.data

import android.content.Context

class NullBytePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()
    }

    fun notificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()
    }

    fun remindersEnabled(): Boolean = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)

    fun setRemindersEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, value).apply()
    }

    fun showTutorialOnLaunch(): Boolean = prefs.getBoolean(KEY_SHOW_TUTORIAL_ON_LAUNCH, false)

    fun setShowTutorialOnLaunch(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TUTORIAL_ON_LAUNCH, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "nullbyte_prefs"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        private const val KEY_SHOW_TUTORIAL_ON_LAUNCH = "show_tutorial_on_launch"
    }
}
