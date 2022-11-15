package com.ownid.demo.ui.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.ownid.demo.R
import com.ownid.demo.databinding.ActivityUserBinding

abstract class BaseUserActivity : AppCompatActivity() {

    abstract fun signOut()
    abstract fun startMainActivity()

    lateinit var binding: ActivityUserBinding

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
            override fun handleOnBackPressed() = Unit // Ignore
        })
    }

    @SuppressLint("SetTextI18n")
    protected fun showUser(name: String, email: String) {
        binding.tvActivityUserWelcome.text = "Welcome $name!"
        binding.tvActivityUserName.text = name
        binding.tvActivityUserEmail.text = email
    }

    fun showError(throwable: Throwable?) {
        val message = throwable?.cause?.message ?: throwable?.message ?: "Unknown error"
        showError(message)
    }

    fun showError(message: String) {
        runOnUiThread {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
                setBackgroundTint(context.getColor(R.color.ownid_error))
            }.show()
        }
    }
}