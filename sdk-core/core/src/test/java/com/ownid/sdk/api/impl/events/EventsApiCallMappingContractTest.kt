package com.ownid.sdk.api.impl.events

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.analytics.ClientError
import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.Step
import com.ownid.sdk.analytics.UserJourneySummary
import com.ownid.sdk.api.ApiUnexpectedFailure
import com.ownid.sdk.api.impl.apiHttpError
import com.ownid.sdk.api.impl.assertJsonPostRequest
import com.ownid.sdk.api.impl.assertUnexpectedApiContractFailure
import com.ownid.sdk.api.impl.jsonBody
import com.ownid.sdk.api.impl.jsonString
import com.ownid.sdk.api.internal.EventsApiFailure
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WIRE-220, WIRE-230, ANALYTICS-010
internal class EventsApiCallMappingContractTest {

    private val serialization = SerializationImpl()
    private val baseUrl = "https://example.com/api/".toHttpUrl()
    private val traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"

    @Test
    fun createBuildsJourneyEventRequestAndPreservesAnalyticsPayloadFields() {
        val call = EventsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            userJourney = userJourneySummary(),
            traceParent = traceParent
        )

        assertJsonPostRequest(call.request, url = "https://example.com/api/event/journey", authorization = null, traceParent = traceParent)

        val body = call.request.jsonBody(serialization)
        assertThat(body.jsonString("id")).isEqualTo("journey-1")
        assertThat(body.jsonString("traceparent")).isEqualTo(traceParent)

        val reporter = body.getValue("reporter").jsonObject
        assertThat(reporter.jsonString("service")).isEqualTo("android-sdk")
        assertThat(reporter.jsonString("origin")).isEqualTo("com.example.app")
        assertThat(reporter.jsonString("referer")).isEqualTo("android-app://com.example.app/login")
        assertThat(reporter.jsonString("version")).isEqualTo("1.2.3")

        val deviceInfo = body.getValue("deviceInfo").jsonObject
        assertThat(deviceInfo.booleanValue("isPlatformAuthenticatorAvailable")).isTrue()
        assertThat(deviceInfo.booleanValue("isWebView")).isFalse()
        assertThat(deviceInfo.booleanValue("isMobileNative")).isTrue()

        val userInfo = body.getValue("userInfo").jsonArray.single().jsonObject
        assertThat(userInfo.getValue("loginId").jsonObject.jsonString("id")).isEqualTo("user@example.com")
        assertThat(userInfo.getValue("loginId").jsonObject.jsonString("type")).isEqualTo("Email")
        assertThat(userInfo.booleanValue("returningUser")).isTrue()
        assertThat(userInfo.jsonString("lastAuthMethod")).isEqualTo("otp")

        val eventInfo = body.getValue("eventInfo").jsonObject
        assertThat(eventInfo.jsonString("type")).isEqualTo("journey-summary")

        val flow = eventInfo.getValue("flows").jsonArray.single().jsonObject
        assertThat(flow.jsonString("id")).isEqualTo("flow-1")
        assertThat(flow.jsonString("name")).isEqualTo("boost-login")
        assertThat(flow.jsonString("source")).isEqualTo("explicit")
        assertThat(flow.jsonString("status")).isEqualTo("completed")
        assertThat(flow.jsonString("startedAt")).isEqualTo("2026-06-19T10:00:00.000Z")
        assertThat(flow.jsonString("completedAt")).isEqualTo("2026-06-19T10:00:02.000Z")
        assertThat(flow.jsonString("switchedToFlow")).isEqualTo("flow-2")

        val flowError = flow.getValue("errors").jsonArray.single().jsonObject
        assertThat(flowError.jsonString("errorCode")).isEqualTo("network")
        assertThat(flowError.jsonString("source")).isEqualTo("events-test")
        assertThat(flowError.jsonString("message")).isEqualTo("diagnostic only")

        val flowInsights = flow.getValue("insights").jsonObject
        assertThat(flowInsights.longValue("duration")).isEqualTo(2_000L)
        assertThat(flowInsights.intValue("clicksCount")).isEqualTo(2)
        assertThat(flowInsights.jsonString("authMethod")).isEqualTo("passkey")
        assertThat(flowInsights.booleanValue("loggedIn")).isTrue()
        assertThat(flowInsights.booleanValue("registered")).isFalse()

        val step = flow.getValue("steps").jsonArray.single().jsonObject
        assertThat(step.jsonString("operationType")).isEqualTo("PasskeyAuth")
        assertThat(step.jsonString("name")).isEqualTo("passkey")
        assertThat(step.jsonString("status")).isEqualTo("failed")
        assertThat(step.jsonString("startedAt")).isEqualTo("2026-06-19T10:00:01.000Z")
        assertThat(step.jsonString("completedAt")).isEqualTo("2026-06-19T10:00:01.500Z")

        val stepError = step.getValue("errors").jsonArray.single().jsonObject
        assertThat(stepError.jsonString("errorCode")).isEqualTo("timeout")
        assertThat(stepError.jsonString("source")).isEqualTo("passkey")
        assertThat(stepError.jsonString("message")).isEqualTo("timed out")

        val stepInsights = step.getValue("insights").jsonObject
        assertThat(stepInsights.longValue("duration")).isEqualTo(500L)
        assertThat(stepInsights.intValue("clicksCount")).isEqualTo(2)
    }

    @Test
    fun analyticsEnumWireValuesRemainStable() {
        assertThat(FlowInfo.Source.values().associate { it.name to it.value }).containsExactly(
            "WIDGET_BUTTON", "widget-button",
            "RETURNING_USER_PROMPT", "returning-user-prompt",
            "RECOVERY_PROMPT", "recovery-prompt",
            "ENROLL_PROMPT", "enroll-prompt",
            "ELITE", "elite",
            "AGENT_AUTHORIZING", "agent-authorizing",
            "DEFERRED", "deferred",
            "EXPLICIT", "explicit",
            "IMPLICIT", "implicit"
        )
        assertThat(FlowInfo.Status.values().associate { it.name to it.value }).containsExactly(
            "ABORTED", "aborted",
            "IN_PROGRESS", "in-progress",
            "COMPLETED", "completed",
            "SWITCHED", "switched",
            "FAILED", "failed"
        )
        assertThat(Step.Status.values().associate { it.name to it.value }).containsExactly(
            "ABORTED", "aborted",
            "IN_PROGRESS", "in-progress",
            "COMPLETED", "completed",
            "FAILED", "failed"
        )
        assertThat(UserJourneySummary.EventInfo.Type.values().associate { it.name to it.value })
            .containsExactly("JOURNEY_SUMMARY", "journey-summary")
    }

    @Test
    fun mapHttpErrorKeepsEventsFailuresOnInternalDiagnosticFailureModel() {
        val call = EventsApiCall.create(
            baseApiUrl = baseUrl,
            serialization = serialization,
            userJourney = userJourneySummary(),
            traceParent = traceParent
        )

        val badRequest = call.mapHttpError(
            apiHttpError(
                url = call.request.url,
                statusCode = 400,
                body = """{"errorCode":"invalid_argument","message":"event payload is invalid"}"""
            )
        )

        assertThat(badRequest).isInstanceOf(EventsApiFailure.BadRequest::class.java)
        badRequest as EventsApiFailure.BadRequest
        assertThat(badRequest.errorCode).isEqualTo(ErrorCode.InvalidArgument)
        assertThat(badRequest.message).isEqualTo("event payload is invalid")

        val malformed = call.mapHttpError(apiHttpError(url = call.request.url, statusCode = 400, body = """{"message":"missing discriminator"}"""))
        assertThat(malformed).isInstanceOf(EventsApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(malformed as ApiUnexpectedFailure, statusCode = 400)

        val unhandledStatus = call.mapHttpError(apiHttpError(url = call.request.url, statusCode = 500, body = """{"message":"server error"}"""))
        assertThat(unhandledStatus).isInstanceOf(EventsApiFailure.Unexpected::class.java)
        assertUnexpectedApiContractFailure(unhandledStatus as ApiUnexpectedFailure, statusCode = 500)
    }

    private fun userJourneySummary(): UserJourneySummary = UserJourneySummary(
        id = "journey-1",
        reporter = UserJourneySummary.Reporter(
            service = UserJourneySummary.Reporter.Service.ANDROID_SDK,
            origin = "com.example.app",
            referer = "android-app://com.example.app/login",
            version = "1.2.3"
        ),
        eventInfo = UserJourneySummary.EventInfo(
            type = UserJourneySummary.EventInfo.Type.JOURNEY_SUMMARY,
            flowsInfo = listOf(
                FlowInfo(
                    id = "flow-1",
                    source = FlowInfo.Source.EXPLICIT,
                    status = FlowInfo.Status.COMPLETED,
                    startedAt = "2026-06-19T10:00:00.000Z",
                    steps = listOf(
                        Step(
                            operationType = OperationType.PASSKEY_AUTH,
                            status = Step.Status.FAILED,
                            startedAt = "2026-06-19T10:00:01.000Z",
                            name = "passkey",
                            completedAt = "2026-06-19T10:00:01.500Z",
                            errors = listOf(ClientError(errorCode = "timeout", source = "passkey", message = "timed out")),
                            insights = Step.Insights(duration = 500L, clicksCount = 2)
                        )
                    ),
                    name = "boost-login",
                    completedAt = "2026-06-19T10:00:02.000Z",
                    errors = listOf(ClientError(errorCode = "network", source = "events-test", message = "diagnostic only")),
                    switchedToFlow = "flow-2",
                    insights = FlowInfo.Insights(
                        duration = 2_000L,
                        clicksCount = 2,
                        authMethod = AuthMethod.Passkey,
                        loggedIn = true,
                        registered = false
                    )
                )
            )
        ),
        deviceInfo = UserJourneySummary.ClientDeviceInfo(
            isPlatformAuthenticatorAvailable = true,
            isWebView = false,
            isMobileNative = true
        ),
        userInfo = listOf(
            UserJourneySummary.UserInfo(
                loginId = LoginId("user@example.com", LoginId.Type.Email),
                returningUser = true,
                lastAuthMethod = AuthMethod.Otp
            )
        )
    )

    private fun JsonObject.booleanValue(key: String): Boolean = getValue(key).jsonPrimitive.boolean
    private fun JsonObject.intValue(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.longValue(key: String): Long = getValue(key).jsonPrimitive.content.toLong()
}
