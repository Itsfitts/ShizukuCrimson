package moe.shizuku.manager.settings

import android.os.Bundle
import moe.shizuku.manager.R

class SettingsFragment : BaseSettingsFragment() {

    override fun onCreateSettingsPreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_main, rootKey)
    }
}
