package com.ownid.sdk

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.ownid.sdk.internal.component.DeviceSecurityStatus
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.config.OwnIdConfigurationService
import com.ownid.sdk.internal.component.events.OwnIdInternalEventsService
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import com.ownid.sdk.internal.component.repository.OwnIdRepositoryService
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Class implements [OwnIdCore]. Holds integration independent components of OwnID SDK.
 */
@OptIn(InternalOwnIdAPI::class)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdCoreImpl private constructor(
    public override val instanceName: InstanceName,
    public override val configuration: Configuration,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val applicationContext: Context,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val correlationId: String,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val okHttpClient: OkHttpClient,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val eventsService: OwnIdInternalEventsService,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val localeService: OwnIdLocaleService,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val repository: OwnIdRepositoryService,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val configurationService: OwnIdConfigurationService
) : OwnIdCore {

    public companion object {
        @MainThread
        @Throws(IllegalStateException::class)
        public fun createInstance(context: Context, instanceName: InstanceName, configuration: Configuration): OwnIdCoreImpl {
            check(Looper.getMainLooper().isCurrentThread) { "OwnID instance must be created on Android main thread" }

            val correlationId: String = UUID.randomUUID().toString()

            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(false)
                .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
//                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

            val deviceSecurityStatus = DeviceSecurityStatus.create(context)?.asJson()
            val eventsService = OwnIdInternalEventsService(configuration, correlationId, deviceSecurityStatus, okHttpClient)

            OwnIdInternalLogger.init(instanceName, eventsService)

            val appContext = context.applicationContext

            val localeService = OwnIdLocaleService(appContext, configuration, okHttpClient)

            val repository = OwnIdRepositoryService.create(appContext, configuration.appId)

            val configurationService = OwnIdConfigurationService(configuration, localeService, appContext, okHttpClient)
            configurationService.ensureConfigurationSet { localeService.updateCurrentOwnIdLocale(context) }

            return OwnIdCoreImpl(
                instanceName,
                configuration,
                appContext,
                correlationId,
                okHttpClient,
                eventsService,
                localeService,
                repository,
                configurationService
            )
        }
    }
}