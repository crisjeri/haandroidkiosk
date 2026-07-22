package cz.hykel.hakiosk

import android.app.Activity
import android.content.pm.ActivityInfo

object OrientationController {
    const val LANDSCAPE = 0
    const val REVERSE_LANDSCAPE = 1
    const val PORTRAIT = 2
    const val REVERSE_PORTRAIT = 3

    fun apply(activity: Activity, preference: Int) {
        val requested = when (preference) {
            REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }
}
