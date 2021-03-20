package org.omnirom.omnistore;

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {
    private val REQUEST_INSTALL_PERMS = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)
        findViewById<Button>(R.id.ok).setOnClickListener {
            if (!hasInstallPermissions()) {
                checkUnknownResourceInstallation()
            } else {
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
