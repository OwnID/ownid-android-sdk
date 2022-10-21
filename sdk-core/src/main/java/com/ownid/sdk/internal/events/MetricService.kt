package com.ownid.sdk.internal.events

import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONObject

@InternalOwnIdAPI
public class MetricService internal constructor(
    private val configuration: Configuration,
    private val correlationId: String,
    private val networkService: EventsNetworkService
) {

    private companion object {
        private const val KEY_SCOPE_CORRELATION_ID = "correlationId"
    }

    @JvmSynthetic
    internal fun sendMetric(
        category: MetricItem.Category,
        type: MetricItem.EventType,
        action: String?,
        context: String,
        errorMessage: String?
    ) {
        networkService.submitMetricRunnable(
            MetricItem(
                category = category,
                type = type,
                action = action,
                context = context,
                metadata = JSONObject().put(KEY_SCOPE_CORRELATION_ID, correlationId),
                errorMessage = errorMessage,
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }
}