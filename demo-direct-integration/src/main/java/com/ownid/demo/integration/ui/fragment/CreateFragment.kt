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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.ownid.demo.integration.DemoApp
import com.ownid.demo.integration.R
import com.ownid.demo.integration.ui.activity.UserActivity
import com.ownid.demo.ui.activity.BaseMainActivity
import com.ownid.demo.ui.removeLinksUnderline
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdPayload
import com.ownid.sdk.event.OwnIdRegisterFlow
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import org.json.JSONObject

class CreateFragment : Fragment() {

    private val ownIdViewModel: OwnIdRegisterViewModel by ownIdViewModel(OwnId.getInstanceOrThrow())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val identityPlatform = (requireActivity().applicationContext as DemoApp).identityPlatform

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_register))

        ownIdViewModel.flowEvents.observe(viewLifecycleOwner) { ownIdFlowEvent ->
            when (ownIdFlowEvent) {
                is OwnIdRegisterFlow.Busy -> Unit

                is OwnIdRegisterFlow.Response -> when (ownIdFlowEvent.payload.type) {
                    OwnIdPayload.Type.Registration -> {
                        // Set the actual login id that was used in OwnID flow
                        if (ownIdFlowEvent.loginId.isNotBlank())
                            view.findViewById<EditText>(R.id.et_fragment_create_email).setText(ownIdFlowEvent.loginId)

                        view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = false
                        view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener {
                            val name = view.findViewById<EditText>(R.id.et_fragment_create_name).text?.toString() ?: ""
                            val email = view.findViewById<EditText>(R.id.et_fragment_create_email).text?.toString() ?: ""

                            // Register user with your identity platform and set OwnId Data to user profile
                            identityPlatform.registerWithOwnId(name, email, ownIdFlowEvent.payload.data) {
                                onFailure { showError(it) }
                                onSuccess { startUserActivity() }
                            }
                        }
                    }

                    OwnIdPayload.Type.Login -> {
                        val token = JSONObject(ownIdFlowEvent.payload.data).getString("token")
                        identityPlatform.getProfile(token) {
                            onSuccess { startUserActivity() }
                            onFailure { showError(it) }
                        }
                    }
                }

                OwnIdRegisterFlow.Undo -> {
                    view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = true
                    view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener { createUserWithEmailAndPassword() }
                }

                is OwnIdRegisterFlow.Error -> showError(ownIdFlowEvent.cause)
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
            WindowCompat.getInsetsController(requireActivity().window, requireView()).hide(WindowInsetsCompat.Type.ime())
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