package com.ownid.demo.gigya.ui.fragment

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
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.toUserMessage
import com.ownid.demo.gigya.ui.activity.UserActivity
import com.ownid.demo.ui.activity.BaseMainActivity
import com.ownid.sdk.OwnId
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.gigya
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel

class LoginFragment : Fragment() {

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    private val ownIdViewModel: OwnIdLoginViewModel by ownIdViewModel(OwnId.gigya)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_login, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ownIdViewModel.attachToView(view.findViewById(R.id.own_id_login))

        ownIdViewModel.events.observe(viewLifecycleOwner) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdLoginEvent.Busy -> Unit

                is OwnIdLoginEvent.LoggedIn -> startUserActivity()

                is OwnIdLoginEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> showError(cause.gigyaError.toUserMessage())
                        else -> showError(cause)
                    }
            }
        }

        view.findViewById<Button>(R.id.b_fragment_login_login).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.et_fragment_login_email).text?.toString() ?: ""
            val password = view.findViewById<EditText>(R.id.et_fragment_login_password).text?.toString() ?: ""

            // Logging in Gigya user without OwnID
            gigya.login(email, password, object : GigyaLoginCallback<GigyaAccount>() {
                override fun onSuccess(account: GigyaAccount?) {
                    if (gigya.isLoggedIn) startUserActivity()
                }

                override fun onError(error: GigyaError) {
                    showError(error.toUserMessage())
                }
            })
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