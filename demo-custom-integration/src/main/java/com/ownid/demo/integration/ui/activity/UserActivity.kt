package com.ownid.demo.integration.ui.activity

import android.content.Intent
import com.ownid.demo.integration.DemoApp
import com.ownid.demo.ui.activity.BaseUserActivity

class UserActivity : BaseUserActivity() {

    override fun signOut() {
        (application as DemoApp).identityPlatform.logout()
    }

    override fun startMainActivity() = startActivity(Intent(this, MainActivity::class.java))

    override fun onResume() {
        super.onResume()

        val currentUser = (application as DemoApp).identityPlatform.currentUser
        showUser(currentUser?.name ?: "<no name>", currentUser?.email ?: "<no email>")
    }
}