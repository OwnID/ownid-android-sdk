package com.ownid.sdk.internal.flow.steps.idcollect

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListAdapter
import android.widget.TextView
import androidx.annotation.RestrictTo
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.flow.AbstractStepUI
import com.ownid.sdk.internal.flow.OwnIdFlowData
import com.ownid.sdk.internal.flow.OwnIdFlowError
import com.ownid.sdk.internal.flow.OwnIdLoginId
import com.ownid.sdk.internal.locale.OwnIdLocaleKey

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class IdCollectStepUI : AbstractStepUI<IdCollectStep>(R.layout.com_ownid_sdk_internal_ui_id_collect) {

    @InternalOwnIdAPI
    internal companion object {
        private const val TAG = "com.ownid.sdk.internal.tag.IdCollectStepUI"

        internal fun show(fragmentManager: FragmentManager): IdCollectStepUI = IdCollectStepUI().apply {
            OwnIdInternalLogger.logD(this, "show", "Invoked")
            show(fragmentManager, TAG)
        }
    }

    private val titleTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_title) }
    private val messageTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_message) }
    private val regionButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_region) }
    private val idEditText: EditText by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_id) }
    private val errorTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_error) }
    private val progress: CircularProgressIndicator by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_progress) }
    private val cancelButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_cancel) }
    private val continueButton: Button by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_continue) }
    private val unspecifiedErrorTextView: TextView by lazy(LazyThreadSafetyMode.NONE) { requireView().findViewById(R.id.com_ownid_sdk_internal_ui_id_collect_unspecified_error) }

    private val backgroundDrawable by lazy(LazyThreadSafetyMode.NONE) {
        AppCompatResources.getDrawable(requireContext(), R.drawable.com_ownid_sdk_internal_ui_id_collect_input_background)
    }

    private val backgroundCountryDrawable by lazy(LazyThreadSafetyMode.NONE) {
        AppCompatResources.getDrawable(requireContext(), R.drawable.com_ownid_sdk_internal_ui_id_collect_country_list_background)
    }

    private val backgroundErrorDrawable by lazy(LazyThreadSafetyMode.NONE) {
        val errorColor = ContextCompat.getColor(requireContext(), R.color.com_ownid_sdk_internal_ui_color_error)
        val drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.com_ownid_sdk_internal_ui_id_collect_input_background)
        (drawable as GradientDrawable).apply { mutate(); setStroke(1.toPx, errorColor) }
    }

    private val emailTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = currentStep.onTextChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isInitSuccessful.not()) return

        when (currentStep.ownIdFlowData.loginId) {
            is OwnIdLoginId.Email -> {
                idEditText.inputType = EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) idEditText.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
                regionButton.visibility = View.GONE
                idEditText.setText(currentStep.ownIdFlowData.loginId.value)
            }

            is OwnIdLoginId.PhoneNumber -> {
                idEditText.inputType = EditorInfo.TYPE_CLASS_PHONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) idEditText.setAutofillHints(View.AUTOFILL_HINT_PHONE)
                regionButton.visibility = View.VISIBLE
                idEditText.setText(currentStep.ownIdFlowData.loginId.value)
                val modalListPopup = ListPopupWindow(requireContext()).also { popup ->
                    popup.isModal = true
                    popup.anchorView = regionButton
                    popup.inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
                    popup.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    val adapter = ArrayAdapter(
                        requireContext(), R.layout.com_ownid_sdk_internal_ui_id_collect_country_list_item,
                        currentStep.ownIdFlowData.ownIdCore.configuration.server.phoneCodes
                    )
                    popup.setAdapter(adapter)
                    popup.width = measureContentWidth(view as ViewGroup, adapter)
                    popup.setBackgroundDrawable(backgroundCountryDrawable)
                    popup.setOnItemClickListener { _, _, position, _ ->
                        currentStep.onPhoneCodeSelected(if (position < 0) popup.selectedItemPosition else position)
                        popup.dismiss()
                    }
                }
                regionButton.setOnClickListener { modalListPopup.show() }
            }

            is OwnIdLoginId.UserName -> TODO()
        }

        idEditText.addTextChangedListener(emailTextWatcher)
        idEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                currentStep.onLoginId(idEditText.text.toString())
                return@setOnEditorActionListener true
            }
            false
        }

        continueButton.setOnClickListener { currentStep.onLoginId(idEditText.text.toString()) }

        cancelButton.setOnClickListener {
            cancelButton.isEnabled = false
            currentStep.onCancel(OwnIdFlowCanceled.ID_COLLECT)
            dismissAllowingStateLoss()
        }

        currentStep.state.observe(viewLifecycleOwner) { state ->
            OwnIdInternalLogger.logD(this, "onViewCreated.state", state.toString())

            progress.isVisible = state.isBusy
            continueButton.isEnabled = state.isBusy.not()

            if (currentStep.ownIdFlowData.loginId is OwnIdLoginId.PhoneNumber) {
                regionButton.text = state.phoneCode!!.toCompactString()
            }

            if (state.error == null) {
                errorTextView.visibility = View.INVISIBLE
                idEditText.background = backgroundDrawable
                showUnspecifiedError(false)
            } else {
                val ownIdUserError = when (state.error) {
                    is IdCollectStep.IdCollectStepWrongLoginId -> {
                        val userMessage = getString(LocaleKeys.getErrorKey(currentStep.ownIdFlowData))
                        OwnIdUserError(OwnIdUserError.Code.INVALID_LOGIN_ID, userMessage, "User entered invalid login id")
                    }

                    is OwnIdFlowError -> state.error.toOwnIdUserError(getString(OwnIdLocaleKey.UNSPECIFIED_ERROR))

                    else -> {
                        val userMessage = getString(OwnIdLocaleKey.UNSPECIFIED_ERROR)
                        OwnIdUserError(OwnIdUserError.Code.UNSPECIFIED, userMessage, "Something went wrong. Please try again.", state.error)
                    }
                }

                if (state.error is OwnIdFlowError && state.error.flowFinished) {
                    continueButton.visibility = View.INVISIBLE
                }

                if (ownIdUserError.isUnspecified()) {
                    showUnspecifiedError(true)
                } else {
                    errorTextView.text = ownIdUserError.userMessage
                    errorTextView.visibility = View.VISIBLE
                    idEditText.background = backgroundErrorDrawable
                }
            }

            if (state.done) dismissAllowingStateLoss()
        }
    }

    override fun onResume() {
        super.onResume()
        idEditText.focusAndShowKeyboard()
    }

    override fun onDestroyView() {
        idEditText.removeTextChangedListener(emailTextWatcher)
        super.onDestroyView()
    }

    override fun onCancel(dialog: DialogInterface) {
        if (isInitSuccessful) currentStep.onCancel(OwnIdFlowCanceled.ID_COLLECT)
        super.onCancel(dialog)
    }

    override fun setStrings() {
        if (isInitSuccessful.not()) return

        titleTextView.text = getString(LocaleKeys.getTitleKey(currentStep.ownIdFlowData))
        messageTextView.text = getString(LocaleKeys.getMessageKey(currentStep.ownIdFlowData))
        cancelButton.text = getString(LocaleKeys.getCancelKey(currentStep.ownIdFlowData))
        continueButton.text = getString(LocaleKeys.getContinueKey(currentStep.ownIdFlowData))
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
        private val prefix = arrayOf("steps", "login-id-collect")

        @JvmStatic
        internal fun getTitleKey(ownIdFlowData: OwnIdFlowData) =
            if (ownIdFlowData.ownIdCore.configuration.isFidoPossible()) OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "title")
                .withFallback(R.string.com_ownid_sdk_internal_ui_steps_id_collect_title)
            else OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "no-biometrics", "title")
                .withFallback(
                    when (ownIdFlowData.loginId) {
                        is OwnIdLoginId.Email -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_title_email_no_biometrics
                        is OwnIdLoginId.PhoneNumber -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_title_phone_no_biometrics
                        is OwnIdLoginId.UserName -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_title_user_id_no_biometrics
                    }
                )

        @JvmStatic
        internal fun getMessageKey(ownIdFlowData: OwnIdFlowData) =
            if (ownIdFlowData.ownIdCore.configuration.isFidoPossible()) OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "message")
                .withFallback(
                    when (ownIdFlowData.loginId) {
                        is OwnIdLoginId.Email -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_message_email
                        is OwnIdLoginId.PhoneNumber -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_message_phone
                        is OwnIdLoginId.UserName -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_message_user_id
                    }
                )
            else OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "no-biometrics", "message")
                .withFallback(R.string.com_ownid_sdk_internal_ui_steps_id_collect_message_no_biometrics)

        @JvmStatic
        internal fun getCancelKey(ownIdFlowData: OwnIdFlowData) =
            OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "cancel")
                .withFallback(R.string.com_ownid_sdk_internal_ui_steps_cancel)

        @JvmStatic
        internal fun getContinueKey(ownIdFlowData: OwnIdFlowData) =
            OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "cta")
                .withFallback(R.string.com_ownid_sdk_internal_ui_steps_id_collect_cta)

        @JvmStatic
        internal fun getErrorKey(ownIdFlowData: OwnIdFlowData) =
            OwnIdLocaleKey(*prefix, ownIdFlowData.loginId.localeKey, "error")
                .withFallback(
                    when (ownIdFlowData.loginId) {
                        is OwnIdLoginId.Email -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_error_email
                        is OwnIdLoginId.PhoneNumber -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_error_phone
                        is OwnIdLoginId.UserName -> R.string.com_ownid_sdk_internal_ui_steps_id_collect_error_user_id
                    }
                )
    }

    private fun measureContentWidth(root: ViewGroup, adapter: ListAdapter): Int {
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var itemView: View? = null
        var maxWidth = 0
        var itemType = 0
        for (index in 0 until adapter.count) {
            val positionType = adapter.getItemViewType(index)
            if (positionType != itemType) {
                itemType = positionType
                itemView = null
            }
            itemView = adapter.getView(index, itemView, root)
            itemView.measure(widthMeasureSpec, heightMeasureSpec)
            maxWidth = Math.max(maxWidth, itemView.measuredWidth)
        }
        return maxWidth
    }
}