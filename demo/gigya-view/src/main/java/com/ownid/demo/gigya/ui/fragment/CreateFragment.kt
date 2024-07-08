package com.ownid.demo.gigya.ui.fragment

import android.os.Bundle
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.ui.activity.MainActivity
import com.ownid.demo.gigya.ui.activity.UserActivity
import com.ownid.demo.gigya.ui.toUserMessage
import com.ownid.sdk.GigyaRegistrationParameters
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import org.json.JSONObject

class CreateFragment : Fragment() {

    private val ownIdViewModel: OwnIdRegisterViewModel by ownIdViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_register))

        ownIdViewModel.integrationEvents.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdRegisterEvent.Busy -> Unit

                is OwnIdRegisterEvent.ReadyToRegister -> {
                    if (ownIdEvent.loginId.isNotBlank()) {
                        view.findViewById<EditText>(R.id.et_fragment_create_email).setText(ownIdEvent.loginId)
                    }
                    view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = false
                }

                OwnIdRegisterEvent.Undo -> {
                    view.findViewById<EditText>(R.id.et_fragment_create_password).isEnabled = true
                }

                is OwnIdRegisterEvent.LoggedIn -> startUserActivity()

                is OwnIdRegisterEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> (requireActivity() as MainActivity).showError(cause.gigyaError.toUserMessage())
                        else -> (requireActivity() as MainActivity).showError(cause)
                    }
            }
        }

        view.findViewById<Button>(R.id.b_fragment_create_create).setOnClickListener {
            if (ownIdViewModel.isReadyToRegister) {
                registerWithOwnId()
            } else {
                registerWithEmailAndPassword()
            }
        }

        view.findViewById<TextView>(R.id.tv_fragment_create_terms).apply {
            movementMethod = LinkMovementMethod.getInstance()
            val spannable = SpannableString(text)
            for (u in spannable.getSpans(0, spannable.length, URLSpan::class.java)) {
                spannable.setSpan(object : URLSpan(u.url) {
                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                    }
                }, spannable.getSpanStart(u), spannable.getSpanEnd(u), 0)
            }
            text = spannable

        }
    }

    private fun registerWithOwnId() {
        val name = requireView().findViewById<EditText>(R.id.et_fragment_create_name).text?.toString() ?: ""
        val email = requireView().findViewById<EditText>(R.id.et_fragment_create_email).text?.toString() ?: ""

        val params = mutableMapOf<String, Any>()
        params["profile"] = JSONObject().put("firstName", name).toString()

        ownIdViewModel.register(email, GigyaRegistrationParameters(params))
    }

    private fun registerWithEmailAndPassword() {
        val name = requireView().findViewById<EditText>(R.id.et_fragment_create_name).text?.toString() ?: ""
        val email = requireView().findViewById<EditText>(R.id.et_fragment_create_email).text?.toString() ?: ""
        val password = requireView().findViewById<EditText>(R.id.et_fragment_create_password).text?.toString() ?: ""

        // Creating Gigya user without OwnID
        val profile = JSONObject().put("firstName", name).toString()
        val params = mutableMapOf<String, Any>("profile" to profile)
        Gigya.getInstance(GigyaAccount::class.java).register(email, password, params, object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount) {
                startUserActivity()
            }

            override fun onError(error: GigyaError) {
                (requireActivity() as MainActivity).showError(error.toUserMessage())
            }
        })
    }

    private fun startUserActivity() {
        requireActivity().apply {
            startActivity(UserActivity.getIntent(this))
            finish()
        }
    }
}