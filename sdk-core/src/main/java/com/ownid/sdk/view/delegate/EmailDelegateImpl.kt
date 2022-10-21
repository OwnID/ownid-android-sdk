package com.ownid.sdk.view.delegate

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.ownid.sdk.InternalOwnIdAPI

internal class EmailDelegateImpl : EmailDelegate {

    private var emailProducer: (() -> String)? = null
    private var emailEditTextView: EditText? = null
    private var emailEditTextViewId: Int = View.NO_ID

    override fun setEmailProducer(producer: (() -> String)?) {
        emailProducer = producer
    }

    override fun setEmailView(emailView: EditText?) {
        emailEditTextView = emailView
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    override fun setEmailViewId(emailViewId: Int) {
        emailEditTextViewId = emailViewId
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    override fun getEmail(view: View): String = when {
        emailProducer != null -> emailProducer!!.invoke()
        emailEditTextView != null -> emailEditTextView!!.text!!.toString()
        emailEditTextViewId != View.NO_ID -> findEditText(view.rootView, emailEditTextViewId)?.text?.toString() ?: ""
        else -> ""
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    override fun getEmailView(view: View): EditText? = when {
        emailProducer != null -> null
        emailEditTextView != null -> emailEditTextView
        emailEditTextViewId != View.NO_ID -> findEditText(view.rootView, emailEditTextViewId)
        else -> null
    }

    private fun findEditText(view: View, id: Int): EditText? {
        return view.findViewById(id) ?: findEditText((view.parent as? ViewGroup) ?: return null, id)
    }
}