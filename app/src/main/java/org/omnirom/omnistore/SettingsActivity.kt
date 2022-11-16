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

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES_WORKER
import org.omnirom.omnistore.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity() {

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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        mBinding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        setSupportActionBar(mBinding.toolbar)

        this.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.title_settings)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, SettingsFragment())
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
