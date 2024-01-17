package com.ownid.demo.integration.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.ownid.demo.integration.DemoApp
import com.ownid.demo.integration.OwnIdIntegration
import com.ownid.demo.integration.R
import com.ownid.demo.integration.ui.activity.UserActivity
import com.ownid.demo.ui.activity.BaseMainActivity
import com.ownid.sdk.OwnId
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel

class LoginFragment : Fragment() {

    private val ownIdIntegration: OwnIdIntegration = OwnId.getInstanceOrThrow(OwnIdIntegration.INSTANCE_NAME)
    private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel(ownIdIntegration)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login))
        ownIdViewModel.events.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdLoginEvent.Busy -> Unit

                is OwnIdLoginEvent.LoggedIn -> startUserActivity()

                is OwnIdLoginEvent.Error -> showError(ownIdEvent.cause)
            }
        }

        view.findViewById<Button>(R.id.b_fragment_login_login).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.et_fragment_login_email).text?.toString() ?: ""
            val password = view.findViewById<EditText>(R.id.et_fragment_login_password).text?.toString() ?: ""

            if (email.isBlank() || password.isBlank()) {
                showError("Please enter all fields")
                return@setOnClickListener
            }

            // Logging in custom user without OwnID
            (requireActivity().applicationContext as DemoApp).identityPlatform.login(email, password) {
                onFailure { showError(it) }
                onSuccess { startUserActivity() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            ViewCompat.getWindowInsetsController(requireView())?.hide(WindowInsetsCompat.Type.ime())
        }, 250)
    }

    private fun startUserActivity() {
        requireActivity().apply {
            startActivity(Intent(this, UserActivity::class.java))
            finish()
        }
    }

    private fun showError(throwable: Throwable?) =
        (requireActivity() as BaseMainActivity).showError(throwable)

    private fun showError(message: String) =
        (requireActivity() as BaseMainActivity).showError(message)
}