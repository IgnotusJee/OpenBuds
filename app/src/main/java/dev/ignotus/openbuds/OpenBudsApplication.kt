package dev.ignotus.openbuds

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log

class OpenBudsApplication : Application(), Thread.UncaughtExceptionHandler {

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, crashCount).apply()
        prefs.edit().putString(KEY_LAST_CRASH, exceptionMessage.take(2000)).apply()

        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    companion object {
        private const val TAG = "OpenBuds"
        private const val PREFS_NAME = "openbuds_crash_prefs"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_LAST_CRASH = "last_crash_message"
    }
}
