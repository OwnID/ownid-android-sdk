package com.ownid.demo.gigya.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.toUserMessage
import com.ownid.demo.ui.activity.BaseUserActivity
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.defaultAuthTokenProvider
import com.ownid.sdk.defaultLoginIdProvider
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel

class UserActivity : BaseUserActivity() {

    internal companion object {
        private const val IS_OWNID_LOGIN = "IS_OWNID_LOGIN"

        internal fun getIntent(context: Context, isOwnidLogin: Boolean): Intent =
            Intent(context, UserActivity::class.java)
                .putExtra(IS_OWNID_LOGIN, isOwnidLogin)
    }

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    private val ownIdViewModel: OwnIdEnrollmentViewModel by ownIdViewModel()

    override fun signOut() = gigya.logout()
    override fun startMainActivity() = startActivity(Intent(this, MainActivity::class.java))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.bActivityUserEnroll.setOnClickListener {
            ownIdViewModel.enrollCredential(
                context = this@UserActivity,
                loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
                force = true
            )
        }

        val isOwnidLogin = intent.getBooleanExtra(IS_OWNID_LOGIN, false)
        if (isOwnidLogin.not()) {
            ownIdViewModel.enrollCredential(
                context = this@UserActivity,
                loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
            )
        }
    }

    override fun onResume() {
        super.onResume()

        gigya.getAccount(true, object : GigyaCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount?) {
                if (account == null) {
                    startMainActivity()
                    finish()
                } else {
                    showUser(account.profile?.firstName ?: "", account.profile?.email ?: "")
                }
            }

            override fun onError(error: GigyaError) = showError(error.toUserMessage())
        })
    }
}