package org.omnirom.omnistore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.omnirom.omnistore.databinding.ActivityIntroBinding
import org.omnirom.omnistore.databinding.SettingsActivityBinding

class IntroActivity : AppCompatActivity() {
    private val REQUEST_INSTALL_PERMS = 0
    private lateinit var mBinding: ActivityIntroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.ok.setOnClickListener {
            if (!hasInstallPermissions()) {
                checkUnknownResourceInstallation()
            } else {
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_INSTALL_PERMS -> {
                finish()
            }
        }
    }

    private fun hasInstallPermissions(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    @Suppress("DEPRECATION")
    private fun checkUnknownResourceInstallation() {
        startActivityForResult(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            ),
            REQUEST_INSTALL_PERMS
        )
    }

}
