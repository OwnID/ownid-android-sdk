package com.ownid.demo.gigya.ui.activity

import android.content.Intent
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.OwnIdGigyaAccount
import com.ownid.demo.ui.activity.BaseUserActivity
import com.ownid.sdk.exception.OwnIdException

class UserActivity : BaseUserActivity() {

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(OwnIdGigyaAccount::class.java) }

    override fun signOut() = gigya.logout()
    override fun startMainActivity() = startActivity(Intent(this, MainActivity::class.java))

    override fun onResume() {
        super.onResume()

        gigya.getAccount(true, object : GigyaCallback<OwnIdGigyaAccount>() {
            override fun onSuccess(account: OwnIdGigyaAccount?) {
                if (account == null) {
                    startMainActivity()
                    finish()
                } else {
                    showUser(account.profile?.firstName ?: "", account.profile?.email ?: "")
                }
            }

            override fun onError(error: GigyaError?) =
                showError(OwnIdException(error?.toString() ?: "Unknown error"))
        })
    }
}