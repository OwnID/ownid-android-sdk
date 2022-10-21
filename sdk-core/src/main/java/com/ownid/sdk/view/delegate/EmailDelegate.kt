package com.ownid.sdk.view.delegate

import android.view.View
import android.widget.EditText
import androidx.annotation.IdRes
import com.ownid.sdk.InternalOwnIdAPI

public interface EmailDelegate {

    /**
     * Set an email producer.
     *
     * If producer is set, then it will be used to get user's email.
     * The value from [setEmailView] and view attribute `emailEditText` will be ignored.
     *
     * @param producer  a function that returns email as a [String]. To remove existing producer, pass `null` as parameter.
     */
    public fun setEmailProducer(producer: (() -> String)?)

    /**
     * Set an email view.
     *
     * If email view is set by this method, then it will be used to get user's email. View attribute `emailEditText` will be ignored.
     * If email producer is set by [setEmailProducer], then email view from this method will be ignored.
     *
     * @param emailView  an [EditText] view for email. To remove existing view, pass `null` as parameter.
     */
    public fun setEmailView(emailView: EditText?)

    @JvmSynthetic
    @InternalOwnIdAPI
    public fun setEmailViewId(@IdRes emailViewId: Int)

    @JvmSynthetic
    @InternalOwnIdAPI
    public fun getEmail(view: View): String

    @JvmSynthetic
    @InternalOwnIdAPI
    public fun getEmailView(view: View): EditText?
}