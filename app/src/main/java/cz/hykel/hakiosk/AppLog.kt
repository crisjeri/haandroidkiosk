package cz.hykel.hakiosk

import android.util.Log

object AppLog {
    fun info(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun warn(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error) else Log.e(tag, message)
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
    }
}
