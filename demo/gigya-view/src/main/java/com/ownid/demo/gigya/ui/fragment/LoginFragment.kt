package com.ownid.demo.gigya.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.ui.activity.MainActivity
import com.ownid.demo.gigya.ui.activity.UserActivity
import com.ownid.demo.gigya.ui.toUserMessage
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel

class LoginFragment : Fragment() {

    private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login))

        ownIdViewModel.integrationEvents.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdLoginEvent.Busy -> Unit

                is OwnIdLoginEvent.LoggedIn -> startUserActivity()

                is OwnIdLoginEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> (requireActivity() as MainActivity).showError(cause.gigyaError.toUserMessage())
                        else -> (requireActivity() as MainActivity).showError(cause)
                    }
            }
        }

        view.findViewById<Button>(R.id.b_fragment_login_login).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.et_fragment_login_email).text?.toString() ?: ""
            val password = view.findViewById<EditText>(R.id.et_fragment_login_password).text?.toString() ?: ""

            // Logging in Gigya user without OwnID
            Gigya.getInstance(GigyaAccount::class.java).login(email, password, object : GigyaLoginCallback<GigyaAccount>() {
                override fun onSuccess(account: GigyaAccount) {
                    startUserActivity()
                }

                override fun onError(error: GigyaError) {
                    (requireActivity() as MainActivity).showError(error.toUserMessage())
                }
            })
        }
    }

    private fun startUserActivity() {
        requireActivity().apply {
            startActivity(UserActivity.getIntent(this))
            finish()
        }
    }
}