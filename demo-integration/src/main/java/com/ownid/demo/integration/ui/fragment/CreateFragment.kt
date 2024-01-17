package com.ownid.demo.integration.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.ownid.demo.integration.DemoApp
import com.ownid.demo.integration.OwnIdIntegration
import com.ownid.demo.integration.R
import com.ownid.demo.integration.ui.activity.UserActivity
import com.ownid.demo.ui.activity.BaseMainActivity
import com.ownid.demo.ui.removeLinksUnderline
import com.ownid.sdk.OwnId
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

class CreateFragment : Fragment() {

    private val ownIdIntegration: OwnIdIntegration = OwnId.getInstanceOrThrow(OwnIdIntegration.INSTANCE_NAME)
    private val ownIdViewModel: OwnIdRegisterViewModel by ownIdViewModel(ownIdIntegration)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_register))
        ownIdViewModel.events.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdRegisterEvent.Busy -> Unit

                is OwnIdRegisterEvent.ReadyToRegister -> {
                    if (ownIdEvent.loginId.isNotBlank()) view.findViewById<EditText>(R.id.et_fragment_create_email)
                        .setText(ownIdEvent.loginId)

                    view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = false
                    view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener {
                        val name = view.findViewById<EditText>(R.id.et_fragment_create_name).text?.toString() ?: ""
                        val email = view.findViewById<EditText>(R.id.et_fragment_create_email).text?.toString() ?: ""

                        ownIdViewModel.register(email, OwnIdIntegration.IntegrationRegistrationParameters(name))
                    }
                }

                OwnIdRegisterEvent.Undo -> {
                    view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = true
                    view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener { createUserWithEmailAndPassword() }
                }

                is OwnIdRegisterEvent.LoggedIn -> startUserActivity()

                is OwnIdRegisterEvent.Error -> showError(ownIdEvent.cause)
            }
        }

        view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener { createUserWithEmailAndPassword() }

        view.findViewById<TextView>(R.id.tv_fragment_create_terms).apply {
            movementMethod = LinkMovementMethod.getInstance()
            removeLinksUnderline()
        }
    }

    private fun createUserWithEmailAndPassword() {
        val name = requireView().findViewById<EditText>(R.id.et_fragment_create_name).text?.toString() ?: ""
        val email = requireView().findViewById<EditText>(R.id.et_fragment_create_email).text?.toString() ?: ""
        val password = requireView().findViewById<EditText>(R.id.et_fragment_create_password).text?.toString() ?: ""

        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            showError("Please enter all fields")
            return
        }

        // Creating custom user without OwnID
        (requireContext().applicationContext as DemoApp).identityPlatform.register(name, email, password) {
            onFailure { showError(it) }
            onSuccess { startUserActivity() }
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