/*
 *  Copyright (C) 2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistore

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import org.omnirom.omnistore.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_CURRENT_DOWNLOADS = "current_downloads"
        const val PREF_CHECK_UPDATES_OLD = "check_updates"
        const val PREF_CURRENT_APPS = "current_apps"
        const val PREF_CURRENT_INSTALLS = "current_installs"
        const val PREF_CHECK_UPDATES_WORKER = "check_updates_worker"
        const val PREF_UPDATE_APPS = "update_apps"
        const val PREF_POST_NOTIFICATION = "post_notifications"
    }

    private lateinit var mBinding: SettingsActivityBinding

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.settings)

            val notificationPreference =
                findPreference<Preference>("notification_settings")
            notificationPreference?.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireActivity().packageName)
                startActivity(intent)
                false
            }

            val checkUpdatesPreference =
                findPreference<TwoStatePreference>(PREF_CHECK_UPDATES_WORKER)
            checkUpdatesPreference?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean)
                    JobUtils().setupWorkManagerJob(requireActivity())
                else
                    JobUtils().cancelWorkManagerJob(requireActivity())
                true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val td = ActivityManager.TaskDescription.Builder()
                .setPrimaryColor(getAttrColor(android.R.attr.colorBackground)).build()
            setTaskDescription(td)
        }

        mBinding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        setSupportActionBar(mBinding.toolbar)

        this.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.title_settings)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(R.id.content, SettingsFragment())
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

    private fun getAttrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

}
