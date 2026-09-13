package app.aaps.plugins.main.general.overview.glass

import android.content.res.Configuration
import android.content.res.Resources
import app.aaps.core.interfaces.sharedPreferences.SP

/**
 * Resolve the dark mode state from Shared Preferences.
 * Handles the "system" value by checking the actual device configuration.
 */
fun resolveIsDarkMode(sp: SP): Boolean {
    return try {
        val mode = sp.getString(app.aaps.core.utils.R.string.key_use_dark_mode, "dark")
        when (mode) {
            "dark"   -> true
            "light"  -> false
            "system" -> {
                val nightModeFlags = Resources.getSystem().configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            else     -> true
        }
    } catch (e: Exception) {
        true
    }
}
