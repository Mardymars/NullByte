package com.nullbyte.app

import android.app.Application
import com.nullbyte.app.notifications.NotificationScheduler

class NullByteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationScheduler.ensureChannels(this)
    }
}
