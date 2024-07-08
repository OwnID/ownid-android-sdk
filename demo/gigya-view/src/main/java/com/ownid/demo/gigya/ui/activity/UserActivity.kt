package com.ownid.demo.gigya.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.google.android.material.snackbar.Snackbar
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.ui.toUserMessage
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.defaultAuthTokenProvider
import com.ownid.sdk.defaultLoginIdProvider
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel

class UserActivity : AppCompatActivity() {

    internal companion object {
        internal fun getIntent(context: Context): Intent = Intent(context, UserActivity::class.java)
    }

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }
    private val ownIdViewModel: OwnIdEnrollmentViewModel by ownIdViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        findViewById<Button>(R.id.b_activity_user_logout).setOnClickListener {
            gigya.logout()
            startActivity(Intent(this@UserActivity, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.b_activity_user_enroll).setOnClickListener {
            ownIdViewModel.enrollCredential(
                context = this@UserActivity,
                loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
                force = true
            )
        }

        ownIdViewModel.enrollCredential(
            context = this@UserActivity,
            loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
            authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
        )
    }

    override fun onResume() {
        super.onResume()

        if (gigya.isLoggedIn.not()) {
            startActivity(Intent(this@UserActivity, MainActivity::class.java))
            finish()
        }

        gigya.getAccount(true, object : GigyaCallback<GigyaAccount>() {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(account: GigyaAccount?) {
                findViewById<TextView>(R.id.tv_activity_user_welcome).text = "Welcome, ${account?.profile?.firstName}"
                findViewById<TextView>(R.id.tv_activity_user_name).text = "Name: ${account?.profile?.firstName}"
                findViewById<TextView>(R.id.tv_activity_user_email).text = "Email: ${account?.profile?.email}"
            }

            override fun onError(error: GigyaError) {
                val message = error.toUserMessage()
                runOnUiThread {
                    Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).apply {
                        setBackgroundTint(context.getColor(R.color.ownid_error))
                    }.show()
                }
            }
        })
    }
}