package com.ownid.demo.gigya.screens.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaPluginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.ui.plugin.GigyaPluginEvent
import com.google.android.material.snackbar.Snackbar
import com.ownid.demo.gigya.screens.DemoApp
import com.ownid.demo.gigya.screens.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val screensSetName = "Default-RegistrationLogin"

        binding.bActivityMainSigninRegister.setOnClickListener {
            gigya.showScreenSet(screensSetName, true, false, mutableMapOf(),
                object : GigyaPluginCallback<GigyaAccount>() {
                    override fun onLogin(accountObj: GigyaAccount) {
                        if (gigya.isLoggedIn) {
                            lifecycle.addObserver(object : DefaultLifecycleObserver {
                                override fun onResume(owner: LifecycleOwner) {
                                    startActivity(Intent(this@MainActivity, UserActivity::class.java))
                                    finish()
                                }
                            })
                        } else {
                            showError("User is not logged in")
                        }
                    }

                    override fun onError(event: GigyaPluginEvent) {
                        showError(GigyaError.errorFrom(event.eventMap).localizedMessage)
                    }

                    override fun onCanceled() {
                        showError("Operation canceled")
                    }
                })
        }
    }

    private fun showError(message: String) {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)

                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
                    setBackgroundTint(context.getColor(com.ownid.demo.R.color.ownid_error))
                }.show()
            }
        })
    }
}