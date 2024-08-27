package com.ownid.sdk.internal.feature.nativeflow.steps.otp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.DialogInterface
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RestrictTo
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.nativeflow.AbstractStepUI
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OtpAuthStepUI : AbstractStepUI<OtpAuthStep>(R.layout.com_ownid_sdk_internal_ui_otp) {

    @InternalOwnIdAPI
    internal companion object {
        private const val TAG = "com.ownid.sdk.internal.tag.OtpAuthStepUI"

        internal fun show(fragmentManager: FragmentManager): OtpAuthStepUI = OtpAuthStepUI().apply {
            OwnIdInternalLogger.logD(this, "show", "Invoked")
            show(fragmentManager, TAG)
        }
    }

    private val titleTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_title) }
    private val messageTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_message) }
    private val unspecifiedErrorTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_unspecified_error) }
    private val descriptionTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_description) }
    private val otpEditText: OwnIdAppCompatEditText by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp) }
    private val resendButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_resend) }
    private val cancelButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_cancel) }
    private val notYouButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_not_you) }
    private val progress: CircularProgressIndicator by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_progress) }
    private val errorTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_otp_error) }

    private val backgroundDrawable by lazy(LazyThreadSafetyMode.NONE) {
        AppCompatResources.getDrawable(requireContext(), R.drawable.com_ownid_sdk_internal_ui_otp_input_background)
    }

    private val backgroundErrorDrawable by lazy(LazyThreadSafetyMode.NONE) {
        val errorColor = ContextCompat.getColor(requireContext(), R.color.com_ownid_sdk_internal_ui_color_error)
        val drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.com_ownid_sdk_internal_ui_otp_input_background)
        (drawable as LayerDrawable).apply { mutate(); this.getDrawable(0).setTint(errorColor) }
    }

    private val otpTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = currentStep.onOtp(otpEditText.text.toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isInitSuccessful.not()) return

        otpEditText.apply {
            flowType = currentStep.ownIdNativeFlowData.flowType
            eventsService = currentStep.ownIdNativeFlowData.ownIdCore.eventsService
            filters = arrayOf(InputFilter.LengthFilter(currentStep.data.otpLength))
            maxEms = currentStep.data.otpLength
            minEms = currentStep.data.otpLength
        }

        resendButton.setOnClickListener { currentStep.onResend() }
        notYouButton.setOnClickListener { currentStep.onNotYou() }
        cancelButton.setOnClickListener {
            cancelButton.isEnabled = false
            currentStep.onCancel(OwnIdFlowCanceled.OTP)
            dismissAllowingStateLoss()
        }

        currentStep.state.observe(viewLifecycleOwner) { state ->
            OwnIdInternalLogger.logD(this, "onViewCreated.state", state.toString())

            progress.isVisible = state.isBusy
            notYouButton.isEnabled = state.isBusy.not()
            notYouButton.isVisible = state.notYouVisible
            resendButton.visibility = when {
                state.isBusy -> View.INVISIBLE
                state.resendVisible -> View.VISIBLE
                else -> View.INVISIBLE
            }

            if (state.error == null) {
                errorTextView.visibility = View.INVISIBLE
                otpEditText.background = backgroundDrawable
                showUnspecifiedError(false)
            } else {
                otpEditText.removeTextChangedListener(otpTextWatcher)
                otpEditText.setText(state.otp)
                otpEditText.addTextChangedListener(otpTextWatcher)

                val ownIdUserError = when (state.error) {
                    is OwnIdNativeFlowError -> state.error.toOwnIdUserError(getString(OwnIdLocaleKey.UNSPECIFIED_ERROR))
                    else -> {
                        val userMessage = getString(OwnIdLocaleKey.UNSPECIFIED_ERROR)
                        OwnIdUserError(OwnIdNativeFlowError.CodeLocal.UNSPECIFIED, userMessage, "Something went wrong. Please try again.", state.error)
                    }
                }

                if (state.error is OwnIdNativeFlowError && state.error.flowFinished) {
                    otpEditText.isEnabled = false
                }

                if (ownIdUserError.isUnspecified()) {
                    showUnspecifiedError(true)
                } else {
                    resendButton.visibility = View.INVISIBLE
                    errorTextView.text = ownIdUserError.userMessage
                    errorTextView.visibility = View.VISIBLE
                    otpEditText.background = backgroundErrorDrawable
                }
            }

            if (state.done) dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        otpEditText.addTextChangedListener(otpTextWatcher)
    }

    override fun onResume() {
        super.onResume()
        otpEditText.focusAndShowKeyboard()
    }

    override fun onStop() {
        otpEditText.removeTextChangedListener(otpTextWatcher)
        super.onStop()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (isInitSuccessful) currentStep.onCancel(OwnIdFlowCanceled.OTP)
        super.onCancel(dialog)
    }

    override fun setStrings() {
        if (isInitSuccessful.not()) return

        titleTextView.text = getString(LocaleKeys.getTitleKey(currentStep.data))
        messageTextView.text = getString(LocaleKeys.getMessageKey(currentStep.data))
            .replace("%CODE_LENGTH%", currentStep.data.otpLength.toString())
            .replace("%LOGIN_ID%", currentStep.ownIdNativeFlowData.loginId.value)
        descriptionTextView.text = getString(LocaleKeys.getDescriptionKey(currentStep.data))
        resendButton.text = getString(LocaleKeys.getResendKey(currentStep.data))
        cancelButton.text = getString(LocaleKeys.getCancelKey(currentStep.data))
        notYouButton.text = getString(LocaleKeys.getNotYouKey(currentStep.data))
        unspecifiedErrorTextView.text = getString(OwnIdLocaleKey.UNSPECIFIED_ERROR)
    }

    private fun showUnspecifiedError(show: Boolean) {
        if (show) {
            unspecifiedErrorTextView.apply {
                if (visibility == View.VISIBLE) return
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(400L).setListener(null)
            }
        } else {
            if (unspecifiedErrorTextView.visibility == View.GONE) return
            unspecifiedErrorTextView.animate().alpha(0f).setDuration(300L).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    unspecifiedErrorTextView.visibility = View.GONE
                }
            })
        }
    }

    private object LocaleKeys {
        private val prefix = arrayOf("steps", "otp")

        @JvmStatic
        internal fun getTitleKey(data: OtpAuthStep.Data) = OwnIdLocaleKey(
            *prefix, data.operationType.name.lowercase(), data.verificationType.name.lowercase(), "title",
        ).withFallback(
            when (data.operationType) {
                OtpAuthStep.OperationType.Sign -> R.string.com_ownid_sdk_internal_ui_steps_otp_title_sign
                OtpAuthStep.OperationType.Verify -> when (data.verificationType) {
                    OtpAuthStep.VerificationType.Email -> R.string.com_ownid_sdk_internal_ui_steps_otp_title_verify_email
                    OtpAuthStep.VerificationType.Sms -> R.string.com_ownid_sdk_internal_ui_steps_otp_title_verify_sms
                }
            }
        )

        @JvmStatic
        internal fun getMessageKey(data: OtpAuthStep.Data) = OwnIdLocaleKey(
            *prefix, data.verificationType.name.lowercase(), data.operationType.name.lowercase(), "message"
        ).withFallback(
            when (data.verificationType) {
                OtpAuthStep.VerificationType.Email -> R.string.com_ownid_sdk_internal_ui_steps_otp_message_email
                OtpAuthStep.VerificationType.Sms -> R.string.com_ownid_sdk_internal_ui_steps_otp_message_sms
            }
        )

        @JvmStatic
        internal fun getDescriptionKey(data: OtpAuthStep.Data) = OwnIdLocaleKey(
            *prefix, data.verificationType.name.lowercase(), data.operationType.name.lowercase(), "description"
        ).withFallback(R.string.com_ownid_sdk_internal_ui_steps_otp_description)

        @JvmStatic
        internal fun getResendKey(data: OtpAuthStep.Data): OwnIdLocaleKey = OwnIdLocaleKey(
            *prefix, data.verificationType.name.lowercase(), data.operationType.name.lowercase(), "resend",
        ).withFallback(
            when (data.verificationType) {
                OtpAuthStep.VerificationType.Email -> R.string.com_ownid_sdk_internal_ui_steps_otp_resend_email
                OtpAuthStep.VerificationType.Sms -> R.string.com_ownid_sdk_internal_ui_steps_otp_resend_sms
            }
        )

        @JvmStatic
        internal fun getCancelKey(data: OtpAuthStep.Data) = OwnIdLocaleKey(
            *prefix, data.verificationType.name.lowercase(), data.operationType.name.lowercase(), "cancel"
        ).withFallback(R.string.com_ownid_sdk_internal_ui_steps_cancel)

        @JvmStatic
        internal fun getNotYouKey(data: OtpAuthStep.Data) = OwnIdLocaleKey(
            *prefix, data.verificationType.name.lowercase(), data.operationType.name.lowercase(), "not-you"
        ).withFallback(R.string.com_ownid_sdk_internal_ui_steps_otp_not_you)
    }
}