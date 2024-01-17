package com.ownid.demo.gigya.screens.ui

import android.content.Intent
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.ui.activity.BaseUserActivity

class UserActivity : BaseUserActivity() {

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    override fun signOut() = gigya.logout()
    override fun startMainActivity() = startActivity(Intent(this, MainActivity::class.java))

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

            override fun onError(error: GigyaError) = showError(Throwable(error.toString()))
        })
    }
}