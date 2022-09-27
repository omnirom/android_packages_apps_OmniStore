package org.omnirom.omnistore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.omnirom.omnistore.databinding.ActivityIntroBinding

class IntroActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityIntroBinding

    private val getPermissions =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                finish()
            }
        }

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

    private fun hasInstallPermissions(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    private fun checkUnknownResourceInstallation() {
        getPermissions.launch(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            )
        )
    }
}
