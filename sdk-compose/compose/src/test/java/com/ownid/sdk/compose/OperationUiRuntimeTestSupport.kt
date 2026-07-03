@file:OptIn(com.ownid.sdk.model.InternalOwnIdAPI::class)

@file:Suppress("unused")

package com.ownid.sdk.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDirection
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.compose.ops.EmailVerificationUiContent
import com.ownid.sdk.compose.ops.EmailVerificationUiProvider
import com.ownid.sdk.compose.ops.LoginIdCollectUiContent
import com.ownid.sdk.compose.ops.LoginIdCollectUiProvider
import com.ownid.sdk.compose.ops.PhoneVerificationUiContent
import com.ownid.sdk.compose.ops.PhoneVerificationUiProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.Reason
import com.ownid.sdk.model.UiError
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationController
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationController
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.OperationResult
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationController
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.EmailVerificationStringsParams
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.LoginIdCollectStrings
import com.ownid.sdk.ui.capability.LoginIdCollectStringsParams
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.OperationUiContainer
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStringsParams
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class MapResolver(private val values: Map<Class<*>, Any> = emptyMap()) : DIContainerResolver {
    override val scopeName: String = "map"
    override fun canResolve(clazz: Class<*>): Boolean = values.containsKey(clazz)
    override fun getUnsatisfiedDependencies(clazz: Class<*>): List<String>? = null
    override fun <T : Any> getOrThrow(clazz: Class<T>): T = getOrNull(clazz) ?: error("Missing ${clazz.name}")
    override fun <T : Any> getOrNull(clazz: Class<T>): T? = clazz.cast(values[clazz])
    override fun <T : Any> getAllInstancesOf(clazz: Class<T>): List<T> =
        values.values.filter { instance -> clazz.isInstance(instance) }.mapNotNull { instance -> clazz.cast(instance) }
}

internal class MutableOperationRegistry(
    initial: Map<OperationID, OperationController<*, *>>
) : OperationRegistry {
    override val current: StateFlow<Map<OperationID, OperationController<*, *>>> = MutableStateFlow(initial)
}

internal class RecordingOperationUiContainer : OperationUiContainer {
    val requests = mutableListOf<ShowRequest>()

    override fun show(instanceName: InstanceName, operationID: OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) {
        requests += ShowRequest(instanceName, operationID)
    }
}

internal data class ShowRequest(val instanceName: InstanceName, val operationID: OperationID)

internal class FakeOperationController<Result, Failure : OperationFailure>(
    override val operationId: OperationID,
    private val settledResult: OperationResult<Result, Failure> = OperationResult.Canceled(Reason.UserClose("closed"))
) : OperationController<Result, Failure> {
    val abortReasons = mutableListOf<Reason>()

    override suspend fun whenSettled(): OperationResult<Result, Failure> = settledResult

    override fun abort(reason: Reason) {
        abortReasons += reason
    }
}

internal interface RecordingOperationController<Success, Failure : OperationFailure> : OperationController<Success, Failure> {
    val abortReasons: List<Reason>
}

internal abstract class CompletingOperationController<Success, Failure : OperationFailure> :
    RecordingOperationController<Success, Failure> {

    private val settled = CompletableDeferred<OperationResult<Success, Failure>>()
    override val abortReasons = mutableListOf<Reason>()

    override suspend fun whenSettled(): OperationResult<Success, Failure> = settled.await()

    override fun abort(reason: Reason) {
        abortReasons += reason
        settle(OperationResult.Canceled(reason))
    }

    protected fun settle(result: OperationResult<Success, Failure>) {
        if (settled.complete(result)) {
            onSettled(result)
        }
    }

    protected abstract fun onSettled(result: OperationResult<Success, Failure>)
}

internal class RecordingLoginIdCollectOperationController(
    override val operationId: OperationID,
    initialUiState: LoginIdCollectUi.UiState = loginUiState()
) : LoginIdCollectOperationController, CompletingOperationController<LoginId, LoginIdCollectOperationFailure>() {
    private val stateFlow = MutableStateFlow<LoginIdCollectOperation.State>(LoginIdCollectOperation.State.Active(initialUiState))

    override val state: StateFlow<LoginIdCollectOperation.State> = stateFlow

    fun complete(result: OperationResult<LoginId, LoginIdCollectOperationFailure>) = settle(result)

    override fun onSettled(result: OperationResult<LoginId, LoginIdCollectOperationFailure>) {
        stateFlow.value = LoginIdCollectOperation.State.Completed(result)
    }
}

internal class RecordingEmailVerificationOperationController(
    override val operationId: OperationID
) : EmailVerificationOperationController, CompletingOperationController<AccessOrProofToken, EmailVerificationOperationFailure>() {
    private val stateFlow = MutableStateFlow<EmailVerificationOperation.State>(EmailVerificationOperation.State.Created)

    override val state: StateFlow<EmailVerificationOperation.State> = stateFlow

    override fun onSettled(result: OperationResult<AccessOrProofToken, EmailVerificationOperationFailure>) {
        stateFlow.value = EmailVerificationOperation.State.Completed(result)
    }
}

internal class RecordingPhoneVerificationOperationController(
    override val operationId: OperationID
) : PhoneVerificationOperationController, CompletingOperationController<AccessOrProofToken, PhoneVerificationOperationFailure>() {
    private val stateFlow = MutableStateFlow<PhoneVerificationOperation.State>(PhoneVerificationOperation.State.Created)

    override val state: StateFlow<PhoneVerificationOperation.State> = stateFlow

    override fun onSettled(result: OperationResult<AccessOrProofToken, PhoneVerificationOperationFailure>) {
        stateFlow.value = PhoneVerificationOperation.State.Completed(result)
    }
}

internal class RecordingLoginIdCollectUiProvider(
    private val content: LoginIdCollectUiContent
) : LoginIdCollectUiProvider {
    override fun getContent(): LoginIdCollectUiContent = content
}

internal class RecordingEmailVerificationUiProvider(
    private val content: EmailVerificationUiContent
) : EmailVerificationUiProvider {
    override fun getContent(): EmailVerificationUiContent = content
}

internal class RecordingPhoneVerificationUiProvider(
    private val content: PhoneVerificationUiContent
) : PhoneVerificationUiProvider {
    override fun getContent(): PhoneVerificationUiContent = content
}

internal class StaticLoginIdCollectStringsProvider(
    strings: LoginIdCollectStrings
) : LoginIdCollectStringsProvider {
    private val flow = MutableStateFlow<LoginIdCollectStrings?>(strings)

    override fun getStrings(params: LoginIdCollectStringsParams): StateFlow<LoginIdCollectStrings?> = flow
}

internal class MutableLoginIdCollectStringsProvider(
    strings: LoginIdCollectStrings
) : LoginIdCollectStringsProvider {
    val requests = mutableListOf<LoginIdCollectStringsParams>()
    private val flow = MutableStateFlow<LoginIdCollectStrings?>(strings)

    fun update(strings: LoginIdCollectStrings) {
        flow.value = strings
    }

    override fun getStrings(params: LoginIdCollectStringsParams): StateFlow<LoginIdCollectStrings?> {
        requests += params
        return flow
    }
}

internal class StaticEmailVerificationStringsProvider(
    strings: EmailVerificationStrings
) : EmailVerificationStringsProvider {
    private val flow = MutableStateFlow<EmailVerificationStrings?>(strings)

    override fun getStrings(params: EmailVerificationStringsParams): StateFlow<EmailVerificationStrings?> = flow
}

internal class StaticPhoneVerificationStringsProvider(
    strings: PhoneVerificationStrings
) : PhoneVerificationStringsProvider {
    private val flow = MutableStateFlow<PhoneVerificationStrings?>(strings)

    override fun getStrings(params: PhoneVerificationStringsParams): StateFlow<PhoneVerificationStrings?> = flow
}

internal fun loginUiState(loginIdValue: String = "person@example.com", error: UiError? = null): LoginIdCollectUi.UiState =
    LoginIdCollectUi.UiState(
        loginIdValue = loginIdValue,
        collectableLoginIdTypes = listOf(LoginId.Type.Email),
        error = error,
        onLoginIdChange = {},
        onContinue = {},
        onCancel = {}
    )

internal fun loginStrings(prefix: String = ""): LoginIdCollectStrings {
    fun value(name: String): String = prefix.takeIf { it.isNotEmpty() }?.let { "$it ${name.lowercase()}" } ?: name
    return LoginIdCollectStrings(
        title = value("Title"),
        message = value("Message"),
        placeholder = value("Placeholder"),
        cancel = value("Cancel"),
        cta = value("Continue"),
        error = value("Error")
    )
}

internal fun emailStrings(prefix: String): EmailVerificationStrings =
    EmailVerificationStrings(
        title = "$prefix title",
        message = "$prefix message",
        description = "$prefix description",
        resend = "$prefix resend",
        cancel = "$prefix cancel",
        notYou = "$prefix not you"
    )

internal fun phoneStrings(prefix: String): PhoneVerificationStrings =
    PhoneVerificationStrings(
        title = "$prefix title",
        message = "$prefix message",
        description = "$prefix description",
        resend = "$prefix resend",
        cancel = "$prefix cancel",
        notYou = "$prefix not you"
    )

internal fun emptyEmailContent(): EmailVerificationUiContent =
    { _, _, _, _, _, _ -> }

internal fun emptyPhoneContent(): PhoneVerificationUiContent =
    { _, _, _, _, _, _ -> }

internal fun SemanticsNodeInteraction.assertEditableText(text: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))

internal fun SemanticsNodeInteraction.assertTextLayoutDirection(textDirection: TextDirection): SemanticsNodeInteraction {
    val textLayoutResults = mutableListOf<TextLayoutResult>()
    val getTextLayoutResult = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertThat(getTextLayoutResult).isNotNull()
    assertThat(getTextLayoutResult!!.action?.invoke(textLayoutResults)).isTrue()
    assertThat(textLayoutResults).isNotEmpty()
    assertThat(textLayoutResults.last().layoutInput.style.textDirection).isEqualTo(textDirection)
    return this
}
