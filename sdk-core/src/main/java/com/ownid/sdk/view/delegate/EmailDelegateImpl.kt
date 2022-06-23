package com.ownid.sdk.view.delegate

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
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

    @InternalOwnIdAPI
    override fun setEmailViewId(emailViewId: Int) {
        emailEditTextViewId = emailViewId
    }

    @InternalOwnIdAPI
    override fun getEmail(view: View): String = when {
        emailProducer != null -> emailProducer!!.invoke()
        emailEditTextView != null -> emailEditTextView!!.text!!.toString()
        emailEditTextViewId != View.NO_ID -> findTextView(view.rootView, emailEditTextViewId)?.text?.toString() ?: ""
        else -> ""
    }

    private fun findTextView(view: View, id: Int): TextView? {
        return view.findViewById(id) ?: findTextView((view.parent as? ViewGroup) ?: return null, id)
    }
}