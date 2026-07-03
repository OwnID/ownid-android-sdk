package com.ownid.sdk.analytics

import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType

/**
 * Collects best-effort analytics for one SDK user journey.
 *
 * SDK flows use this internal contract to record flow metadata, operation starts, clicks, operation completions,
 * login ID hints, referer data, and a terminal outcome. Completion schedules a journey summary submission.
 * Analytics failures are diagnostic only and must not decide SDK flow or operation results.
 */
@InternalOwnIdAPI
internal interface UserJourney {
    /**
     * Starts tracking a flow or updates the active flow metadata.
     *
     * If a flow is already active, the collector updates its source and non-null name instead of settling it.
     * Use [Controller.switchToFlow] when one flow should be marked as switched before another flow starts.
     *
     * @param name Optional flow name used in analytics.
     * @param source Origin that started or resumed the flow.
     * @param traceParent Optional trace context seed forwarded with the analytics summary.
     */
    suspend fun startFlow(name: String?, source: FlowInfo.Source, traceParent: String? = null): Controller

    /**
     * Mutable handle for the active flow in this journey.
     *
     * Calls are telemetry updates only. They describe flow and operation progress after it happens; they do not control
     * the flow state machine, operation settlement, UI state, provider callbacks, or app authentication.
     */
    interface Controller {
        /**
         * Adds a login ID hint to the journey.
         *
         * Implementations may enrich the entry with returning-user metadata when the local user repository contains the
         * same login ID. The hint is privacy-sensitive analytics data and must not be treated as authenticated user state.
         */
        suspend fun setUserInfo(loginId: LoginId)

        /** Replaces the referer URL or screen identifier reported for the journey. */
        suspend fun setReferer(referer: String)

        /**
         * Marks the active flow as switched and starts tracking the target flow.
         *
         * In-progress operation steps in the previous flow are recorded as aborted. [flowId] is used as the target flow
         * identifier when present; otherwise the implementation creates one.
         */
        suspend fun switchToFlow(flowId: String? = null, name: String?, source: FlowInfo.Source)

        /** Records that an operation step started in the active flow. */
        suspend fun startOperation(operationId: OperationID)

        /** Increments the click counter for an operation step when that step is known to the active flow. */
        suspend fun addOperationClick(operationId: OperationID)

        /**
         * Records operation step completion and optional diagnostic metadata.
         *
         * A missing [errorCode] records success, [ErrorCode.Aborted] records an aborted step, and other errors record a
         * failed step with the supplied source and message.
         */
        suspend fun completeOperation(operationId: OperationID, errorCode: ErrorCode? = null, source: String? = null, message: String? = null)

        /** Settles the active flow and schedules journey summary submission. */
        fun completeFlow(outcome: Outcome)

        /** Terminal analytics outcome for a flow. */
        sealed class Outcome {
            /** Flow completed with a login. */
            class LoggedIn(val authMethod: AuthMethod) : Outcome()

            /** Flow completed with registration. */
            class Registered(val authMethod: AuthMethod?) : Outcome()

            /** Flow completed without a narrower login or registration classification. */
            class Completed(val authMethod: AuthMethod?) : Outcome()

            /** Flow completed with a diagnostic error classification. */
            class Error(val errorCode: ErrorCode, val source: String? = null, val message: String? = null) : Outcome()
        }
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class UserJourneySummary(
    val id: String,
    val reporter: Reporter,
    val eventInfo: EventInfo,
    val deviceInfo: ClientDeviceInfo,
    val userInfo: List<UserInfo>
) {
    internal class Reporter(val service: Service, val origin: String, val referer: String, val version: String? = null) {
        internal enum class Service(val value: String) {
            WEB_SDK("web-sdk"),
            ANDROID_SDK("android-sdk"),
            IOS_SDK("ios-sdk");
        }
    }

    internal class EventInfo(val type: Type, val flowsInfo: List<FlowInfo>) {
        internal enum class Type(val value: String) {
            JOURNEY_SUMMARY("journey-summary");
        }
    }

    internal class ClientDeviceInfo(val isPlatformAuthenticatorAvailable: Boolean, val isWebView: Boolean, val isMobileNative: Boolean)

    internal class UserInfo(
        val loginId: LoginId,
        val returningUser: Boolean? = null,
        val lastAuthMethod: AuthMethod? = null /*For returningUser*/
    )
}

internal class ClientError(val errorCode: String, val source: String? = null, val message: String? = null)


/**
 * Analytics record for a single flow inside a user journey.
 *
 * This SPI model carries flow identity, source, status, timestamps, switched-flow linkage, errors, steps, and insights
 * across SDK analytics boundaries. It is not an app-developer integration surface.
 */
@InternalOwnIdAPI
public class FlowInfo internal constructor(
    internal val id: String,
    internal val source: Source,
    internal val status: Status,
    internal val startedAt: String,
    internal val steps: List<Step>,
    internal val name: String? = null,
    internal val completedAt: String? = null,
    internal val errors: List<ClientError>? = null,
    internal val switchedToFlow: String? = null,
    internal val insights: Insights? = null
) {

    @InternalOwnIdAPI
    public enum class Source(public val value: String) {
        WIDGET_BUTTON("widget-button"),
        RETURNING_USER_PROMPT("returning-user-prompt"),
        RECOVERY_PROMPT("recovery-prompt"),
        ENROLL_PROMPT("enroll-prompt"),
        ELITE("elite"),
        AGENT_AUTHORIZING("agent-authorizing"),
        DEFERRED("deferred"),
        EXPLICIT("explicit"),
        IMPLICIT("implicit");
    }

    internal enum class Status(val value: String) {
        ABORTED("aborted"),
        IN_PROGRESS("in-progress"),
        COMPLETED("completed"),
        SWITCHED("switched"),
        FAILED("failed");
    }

    internal class Insights(
        val duration: Long? = null, /* Duration in milliseconds. */
        val errorRate: Double? = null, /* Percentage of errors encountered in the flow. */
        val retries: Int? = null,
        val clicksCount: Int? = null,
        val authMethod: AuthMethod? = null, /* Authentication method reported by the terminal outcome. */
        val loggedIn: Boolean? = null,  /* Indicates whether the terminal outcome was login. */
        val registered: Boolean? = null /* Indicates whether the terminal outcome was registration. */
    )
}

internal class Step(
    val operationType: OperationType,
    val status: Status,
    val startedAt: String,
    val name: String? = null,
    val completedAt: String? = null,
    val errors: List<ClientError>? = null,
    val insights: Insights? = null
) {
    internal enum class Status(val value: String) {
        ABORTED("aborted"),
        IN_PROGRESS("in-progress"),
        COMPLETED("completed"),
        FAILED("failed");
    }

    internal class Insights(val duration: Long? = null, val retries: Int? = null, val clicksCount: Int? = null)
}
