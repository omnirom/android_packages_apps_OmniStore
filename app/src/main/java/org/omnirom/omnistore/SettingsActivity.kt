package org.omnirom.omnistore

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES

class SettingsActivity : AppCompatActivity() {

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings)

            val notificationPreference =
                findPreference<Preference>("notification_settings")
            notificationPreference?.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity!!.packageName)
                startActivity(intent)
                false
            }

            val checkUpdatesPreference =
                findPreference<SwitchPreference>(PREF_CHECK_UPDATES)
            checkUpdatesPreference?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean)
                    JobUtils().scheduleCheckUpdates(activity!!)
                else
                    JobUtils().cancelCheckForUpdates(activity!!)
                true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}