package com.ownid.sdk.api.impl.events

import com.ownid.sdk.analytics.FlowInfo
import com.ownid.sdk.analytics.Step
import com.ownid.sdk.analytics.UserJourneySummary
import com.ownid.sdk.api.ApiResult
import com.ownid.sdk.api.impl.ApiCall
import com.ownid.sdk.api.impl.models.oneof.mapBadRequestErrorResponse
import com.ownid.sdk.api.impl.runApiMappingCatching
import com.ownid.sdk.api.impl.toApiModel
import com.ownid.sdk.api.impl.toUnexpectedFailure
import com.ownid.sdk.api.impl.toUnexpectedStatusFail
import com.ownid.sdk.api.internal.EventsApiFailure
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.InternalOwnIdAPI
import okhttp3.HttpUrl
import okhttp3.Request

@OptIn(InternalOwnIdAPI::class)
internal class EventsApiCall(
    private val serialization: Serialization,
    override val request: Request
) : ApiCall<Unit, EventsApiFailure> {

    override fun mapHttpSuccess(successResponse: Network.Response.Success): ApiResult<Unit, EventsApiFailure> = when (successResponse.code) {
        204 -> ApiResult.Success(Unit)
        else -> ApiResult.Failure(mapUnhandled(successResponse.toUnexpectedStatusFail()))
    }

    override fun mapHttpError(failResponse: Network.Response.Fail.HttpError): EventsApiFailure = when (failResponse.statusCode) {
        400 -> runApiMappingCatching {
            serialization.mapBadRequestErrorResponse(
                body = failResponse.body,
                invalidArgument = EventsApiFailure::BadRequest,
                unknown = EventsApiFailure::BadRequest
            )
        }.getOrElse { error ->
            failResponse.toUnexpectedFailure(EventsApiFailure::Unexpected, error)
        }

        else -> failResponse.toUnexpectedFailure(EventsApiFailure::Unexpected)
    }

    override fun mapUnhandled(failResponse: Network.Response.Fail): EventsApiFailure =
        failResponse.toUnexpectedFailure(EventsApiFailure::Unexpected)

    internal companion object {
        internal fun create(
            baseApiUrl: HttpUrl,
            serialization: Serialization,
            userJourney: UserJourneySummary,
            traceParent: String,
        ): EventsApiCall {
            val journey = com.ownid.sdk.api.impl.models.UserJourneySummary(
                id = userJourney.id,
                traceparent = traceParent,
                reporter = userJourney.reporter.let {
                    com.ownid.sdk.api.impl.models.Reporter(
                        service = when (it.service) {
                            UserJourneySummary.Reporter.Service.WEB_SDK -> com.ownid.sdk.api.impl.models.ReporterService.WEB_SDK
                            UserJourneySummary.Reporter.Service.ANDROID_SDK -> com.ownid.sdk.api.impl.models.ReporterService.ANDROID_SDK
                            UserJourneySummary.Reporter.Service.IOS_SDK -> com.ownid.sdk.api.impl.models.ReporterService.IOS_SDK
                        },
                        origin = it.origin,
                        referer = it.referer,
                        version = it.version
                    )
                },
                eventInfo = userJourney.eventInfo.let { eventInfo ->
                    com.ownid.sdk.api.impl.models.EventInfo(
                        type = when (eventInfo.type) {
                            UserJourneySummary.EventInfo.Type.JOURNEY_SUMMARY -> com.ownid.sdk.api.impl.models.EventInfoType.JOURNEY_SUMMARY
                        },
                        flows = eventInfo.flowsInfo.map { flow ->
                            com.ownid.sdk.api.impl.models.Flow(
                                id = flow.id,
                                source = when (flow.source) {
                                    FlowInfo.Source.WIDGET_BUTTON -> com.ownid.sdk.api.impl.models.FlowSource.WIDGET_BUTTON
                                    FlowInfo.Source.RETURNING_USER_PROMPT -> com.ownid.sdk.api.impl.models.FlowSource.RETURNING_USER_PROMPT
                                    FlowInfo.Source.RECOVERY_PROMPT -> com.ownid.sdk.api.impl.models.FlowSource.RECOVERY_PROMPT
                                    FlowInfo.Source.ENROLL_PROMPT -> com.ownid.sdk.api.impl.models.FlowSource.ENROLL_PROMPT
                                    FlowInfo.Source.ELITE -> com.ownid.sdk.api.impl.models.FlowSource.ELITE
                                    FlowInfo.Source.AGENT_AUTHORIZING -> com.ownid.sdk.api.impl.models.FlowSource.AGENT_AUTHORIZING
                                    FlowInfo.Source.DEFERRED -> com.ownid.sdk.api.impl.models.FlowSource.DEFERRED
                                    FlowInfo.Source.EXPLICIT -> com.ownid.sdk.api.impl.models.FlowSource.EXPLICIT
                                    FlowInfo.Source.IMPLICIT -> com.ownid.sdk.api.impl.models.FlowSource.IMPLICIT
                                },
                                status = when (flow.status) {
                                    FlowInfo.Status.ABORTED -> com.ownid.sdk.api.impl.models.FlowStatus.ABORTED
                                    FlowInfo.Status.IN_PROGRESS -> com.ownid.sdk.api.impl.models.FlowStatus.IN_PROGRESS
                                    FlowInfo.Status.COMPLETED -> com.ownid.sdk.api.impl.models.FlowStatus.COMPLETED
                                    FlowInfo.Status.SWITCHED -> com.ownid.sdk.api.impl.models.FlowStatus.SWITCHED
                                    FlowInfo.Status.FAILED -> com.ownid.sdk.api.impl.models.FlowStatus.FAILED
                                },
                                startedAt = flow.startedAt,
                                steps = flow.steps.map { step ->
                                    com.ownid.sdk.api.impl.models.Step(
                                        operationType = step.operationType.toApiModel(),
                                        status = when (step.status) {
                                            Step.Status.ABORTED -> com.ownid.sdk.api.impl.models.StepStatus.ABORTED
                                            Step.Status.IN_PROGRESS -> com.ownid.sdk.api.impl.models.StepStatus.IN_PROGRESS
                                            Step.Status.COMPLETED -> com.ownid.sdk.api.impl.models.StepStatus.COMPLETED
                                            Step.Status.FAILED -> com.ownid.sdk.api.impl.models.StepStatus.FAILED
                                        },
                                        startedAt = step.startedAt,
                                        name = step.name,
                                        completedAt = step.completedAt,
                                        errors = step.errors?.map {
                                            com.ownid.sdk.api.impl.models.ClientError(
                                                errorCode = it.errorCode,
                                                source = it.source,
                                                message = it.message
                                            )

                                        },
                                        insights = step.insights?.let { insights ->
                                            com.ownid.sdk.api.impl.models.StepInsights(
                                                duration = insights.duration,
                                                retries = insights.retries,
                                                clicksCount = insights.clicksCount,
                                            )
                                        }
                                    )
                                },
                                name = flow.name,
                                completedAt = flow.completedAt,
                                errors = flow.errors?.map {
                                    com.ownid.sdk.api.impl.models.ClientError(
                                        errorCode = it.errorCode,
                                        source = it.source,
                                        message = it.message
                                    )
                                },
                                switchedToFlow = flow.switchedToFlow,
                                insights = flow.insights?.let { insights ->
                                    com.ownid.sdk.api.impl.models.FlowInsights(
                                        duration = insights.duration,
                                        errorRate = insights.errorRate,
                                        retries = insights.retries,
                                        clicksCount = insights.clicksCount,
                                        authMethod = when (insights.authMethod) {
                                            AuthMethod.Otp -> com.ownid.sdk.api.impl.models.AuthMethod.OTP
                                            AuthMethod.Passkey -> com.ownid.sdk.api.impl.models.AuthMethod.PASSKEY
                                            AuthMethod.MagicLink -> com.ownid.sdk.api.impl.models.AuthMethod.MAGIC_LINK
                                            AuthMethod.Password -> com.ownid.sdk.api.impl.models.AuthMethod.PASSWORD
                                            AuthMethod.Deferred -> com.ownid.sdk.api.impl.models.AuthMethod.DEFERRED
                                            AuthMethod.Immediate -> com.ownid.sdk.api.impl.models.AuthMethod.IMMEDIATE
                                            AuthMethod.SocialGoogle -> com.ownid.sdk.api.impl.models.AuthMethod.SOCIAL_GOOGLE
                                            AuthMethod.SocialApple -> com.ownid.sdk.api.impl.models.AuthMethod.SOCIAL_APPLE
                                            AuthMethod.FaceKey -> com.ownid.sdk.api.impl.models.AuthMethod.FACEKEY
                                            AuthMethod.Unknown -> com.ownid.sdk.api.impl.models.AuthMethod.UNKNOWN
                                            null -> null
                                        },
                                        loggedIn = insights.loggedIn,
                                        registered = insights.registered,
                                    )
                                }
                            )
                        }
                    )
                },
                deviceInfo = userJourney.deviceInfo.let { deviceInfo ->
                    com.ownid.sdk.api.impl.models.ClientDeviceInfo(
                        isPlatformAuthenticatorAvailable = deviceInfo.isPlatformAuthenticatorAvailable,
                        isWebView = deviceInfo.isWebView,
                        isMobileNative = deviceInfo.isMobileNative
                    )
                },
                userInfo = userJourney.userInfo.map {
                    com.ownid.sdk.api.impl.models.UserInfo(
                        loginId = com.ownid.sdk.api.impl.models.LoginId(id = it.loginId.id, type = it.loginId.type.toApiModel()),
                        returningUser = it.returningUser,
                        lastAuthMethod = when (it.lastAuthMethod) {
                            AuthMethod.Otp -> com.ownid.sdk.api.impl.models.AuthMethod.OTP
                            AuthMethod.Passkey -> com.ownid.sdk.api.impl.models.AuthMethod.PASSKEY
                            AuthMethod.MagicLink -> com.ownid.sdk.api.impl.models.AuthMethod.MAGIC_LINK
                            AuthMethod.Password -> com.ownid.sdk.api.impl.models.AuthMethod.PASSWORD
                            AuthMethod.Deferred -> com.ownid.sdk.api.impl.models.AuthMethod.DEFERRED
                            AuthMethod.Immediate -> com.ownid.sdk.api.impl.models.AuthMethod.IMMEDIATE
                            AuthMethod.SocialGoogle -> com.ownid.sdk.api.impl.models.AuthMethod.SOCIAL_GOOGLE
                            AuthMethod.SocialApple -> com.ownid.sdk.api.impl.models.AuthMethod.SOCIAL_APPLE
                            AuthMethod.FaceKey -> com.ownid.sdk.api.impl.models.AuthMethod.FACEKEY
                            AuthMethod.Unknown -> com.ownid.sdk.api.impl.models.AuthMethod.UNKNOWN
                            null -> null
                        }
                    )
                }
            )
            val request = Network.Request.Builder(
                url = baseApiUrl.newBuilder().addEncodedPathSegments("event/journey").build()
            ).apply {
                body = serialization.encodeToString(com.ownid.sdk.api.impl.models.UserJourneySummary.serializer(), journey)
                queueTraceParent(traceParent)
            }.build()

            return EventsApiCall(serialization, request)
        }
    }
}
