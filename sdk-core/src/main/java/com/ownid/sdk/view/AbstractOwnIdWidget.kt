package com.ownid.sdk.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.locale.OwnIdLocaleService
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlin.math.roundToInt

/**
 * Common OwnID view functionality.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class AbstractOwnIdWidget(
    context: Context,
    attrs: AttributeSet?,
    @AttrRes defStyleAttr: Int,
    @StyleRes defStyleRes: Int
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), OwnIdLocaleService.LocaleUpdateListener {

    protected var ownIdViewModel: OwnIdBaseViewModel? = null

    private var loginId: String? = null
    private var loginIdProvider: (() -> String)? = null
    private var loginIdEditTextView: EditText? = null
    private var loginIdEditTextViewId: Int = View.NO_ID
    private var loginIdChangeListener: Function0<Unit>? = null
    private val textChangedRunnable = Runnable { loginIdChangeListener?.invoke() }
    private val loginIdTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            removeCallbacks(textChangedRunnable)
            postDelayed(textChangedRunnable, 500)
        }
    }

    /**
     * Set a Login ID.
     *
     * If Login ID is set, then it will be used as user's Login ID.
     * The value from [setLoginIdViewId], [setLoginIdView]  [setLoginIdProvider] and view attribute `loginIdEditText` will be ignored.
     *
     * @param loginId  A Login ID as a [String]. To remove Login ID, pass `null` as parameter.
     */
    public fun setLoginId(loginId: String?) {
        this.loginId = loginId
        removeCallbacks(textChangedRunnable)
        postDelayed(textChangedRunnable, 500)
    }

    /**
     * Set a Login ID provider.
     *
     * If Login ID provider is set, then it will be used to get user's Login ID.
     * The value from [setLoginIdViewId], [setLoginIdView] and view attribute `loginIdEditText` will be ignored.
     * If Login ID is set by [setLoginId], then Login ID value from this method will be ignored.
     *
     * @param loginIdProvider  A function that returns Login ID as a [String]. To remove Login ID provider, pass `null` as parameter.
     */
    public fun setLoginIdProvider(loginIdProvider: (() -> String)?) {
        this.loginIdProvider = loginIdProvider
        removeCallbacks(textChangedRunnable)
        postDelayed(textChangedRunnable, 500)
    }

    /**
     * Set an Login ID view.
     *
     * If Login ID view is set by this method, then it will be used to get user's Login ID.
     * The value from [setLoginIdViewId] and view attribute `loginIdEditText` will be ignored.
     * If Login ID is set by [setLoginId] or [setLoginIdProvider], then Login ID view from this method will be ignored.
     *
     * @param loginIdView  An [EditText] view for Login ID. To remove existing view, pass `null` as parameter.
     */
    public fun setLoginIdView(loginIdView: EditText?) {
        if (loginIdView == null) {
            loginIdEditTextView?.removeTextChangedListener(loginIdTextWatcher)
            removeCallbacks(textChangedRunnable)
        }

        loginIdEditTextView = loginIdView
    }

    /**
     * Set an Login ID view id.
     *
     * If Login ID view id is set by this method, then it will be used to get user's Login ID.
     * The value from view attribute `loginIdEditText` will be ignored.
     * If Login ID is set by [setLoginId], [setLoginIdProvider] or [setLoginIdView] then Login ID view from this method will be ignored.
     *
     * @param loginIdViewId  An id of Login ID view. To remove existing view id, pass `View.NO_ID` as parameter.
     */
    public fun setLoginIdViewId(@IdRes loginIdViewId: Int) {
        if (loginIdViewId == View.NO_ID && loginIdEditTextViewId != View.NO_ID) {
            findEditText(rootView, loginIdEditTextViewId)?.removeTextChangedListener(loginIdTextWatcher)
            removeCallbacks(textChangedRunnable)
        }

        loginIdEditTextViewId = loginIdViewId
    }

    @JvmSynthetic
    internal fun getLoginId(): String = when {
        loginId != null -> loginId!!
        loginIdProvider != null -> loginIdProvider!!.invoke()
        loginIdEditTextView != null -> loginIdEditTextView?.text?.toString() ?: ""
        loginIdEditTextViewId != View.NO_ID -> findEditText(rootView, loginIdEditTextViewId)?.text?.toString() ?: ""
        else -> ""
    }

    protected fun setLoginIdChangeListener(listener: (() -> Unit)?) {
        loginIdChangeListener = listener

        if (listener == null) {
            loginIdEditTextView?.removeTextChangedListener(loginIdTextWatcher)
            if (loginIdEditTextViewId != View.NO_ID)
                findEditText(rootView, loginIdEditTextViewId)?.removeTextChangedListener(loginIdTextWatcher)
            removeCallbacks(textChangedRunnable)
            return
        }

        when {
            loginIdEditTextView != null -> loginIdEditTextView?.addTextChangedListener(loginIdTextWatcher)
            loginIdEditTextViewId != View.NO_ID -> findEditText(rootView, loginIdEditTextViewId)?.addTextChangedListener(loginIdTextWatcher)
        }
    }

    private fun findEditText(view: View, id: Int): EditText? {
        return view.findViewById(id) ?: findEditText((view.parent as? ViewGroup) ?: return null, id)
    }

    @CallSuper
    @MainThread
    @JvmSynthetic
    internal open fun setViewModel(viewModel: OwnIdBaseViewModel) {
        ownIdViewModel = viewModel

        val scope = requireNotNull(viewModel.viewLifecycleCoroutineScope)

        scope.coroutineContext.job.invokeOnCompletion {
            ownIdViewModel = null
            loginIdProvider = null
        }

        viewModel.ownIdResponseFlow.onEach { setHasOwnIdResponse(it != null) }.launchIn(scope)
        viewModel.busyFlow.onEach { onBusy(it) }.launchIn(scope)
    }

    internal abstract fun getMetadata(): Metadata

    public abstract fun onBusy(isBusy: Boolean)

    @CallSuper
    protected open fun setHasOwnIdResponse(value: Boolean) {
        OwnIdInternalLogger.logD(this, "setHasOwnIdResponse", value.toString())
    }

    protected fun getLocaleService(): OwnIdLocaleService? = ownIdViewModel?.ownIdCore?.localeService

    protected abstract fun setStrings()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.not()) {
            getLocaleService()?.registerLocaleUpdateListener(this)
            onLocaleUpdated()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode.not()) getLocaleService()?.unregisterLocaleUpdateListener(this)
    }

    override fun onLocaleUpdated() {
        setStrings()
    }

    protected val Number.toPx: Int get() = (this.toFloat() * this@AbstractOwnIdWidget.resources.displayMetrics.density).roundToInt()
}