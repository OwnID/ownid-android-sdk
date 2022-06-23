package com.ownid.demo.ui.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.ownid.demo.R
import com.ownid.demo.databinding.ActivityUserBinding

abstract class BaseUserActivity : AppCompatActivity() {

    abstract fun signOut()
    abstract fun startMainActivity()

    lateinit var binding: ActivityUserBinding

    protected var name: String = ""
    protected var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bActivityUserLogout.setOnClickListener {
            signOut()
            startMainActivity()
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Ignore
            }
        })

        showUser(savedInstanceState?.getString(NAME_KEY) ?: "", savedInstanceState?.getString(EMAIL_KEY) ?: "")
    }

    @SuppressLint("SetTextI18n")
    protected fun showUser(name: String, email: String) {
        this.name = name
        this.email = email
        binding.tvActivityUserWelcome.text = "Welcome $name!"
        binding.tvActivityUserName.text = name
        binding.tvActivityUserEmail.text = email
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(NAME_KEY, name)
        outState.putString(EMAIL_KEY, email)
        super.onSaveInstanceState(outState)
    }

    fun showError(throwable: Throwable?) {
        val message = throwable?.cause?.message ?: throwable?.message ?: "Unknown error"
        showError(message)
    }

    fun showError(message: String) {
        runOnUiThread {
            Log.e(this.javaClass.simpleName, "showError: $message")

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
                setBackgroundTint(context.getColor(R.color.ownid_error))
            }.show()
        }
    }

    private companion object {
        private const val NAME_KEY = "NAME_KEY"
        private const val EMAIL_KEY = "EMAIL_KEY"
    }
}