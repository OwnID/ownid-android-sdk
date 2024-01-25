package com.ownid.sdk

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.OwnIdStorageService
import com.ownid.sdk.internal.config.OwnIdConfigurationService
import com.ownid.sdk.internal.events.OwnIdInternalEventsService
import com.ownid.sdk.internal.locale.OwnIdLocaleService
import com.ownid.sdk.internal.webbridge.OwnIdWebViewBridgeImpl
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Class implements [OwnIdCore]. Holds integration independent components of OwnID SDK.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdCoreImpl private constructor(
    public override val instanceName: InstanceName,
    public override val configuration: Configuration,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val correlationId: String,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val okHttpClient: OkHttpClient,
    @get:JvmSynthetic @property:InternalOwnIdAPI public val eventsService: OwnIdInternalEventsService,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val localeService: OwnIdLocaleService,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val storageService: OwnIdStorageService,
    @get:JvmSynthetic @property:InternalOwnIdAPI internal val configurationService: OwnIdConfigurationService
) : OwnIdCore {

    public companion object {
        @MainThread
        @Throws(IllegalStateException::class)
        public fun createInstance(context: Context, instanceName: InstanceName, configuration: Configuration): OwnIdCoreImpl {
            check(Looper.getMainLooper().isCurrentThread) { "OwnID instance must be created on Android Main thread" }

            val correlationId: String = UUID.randomUUID().toString()

            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(false)
                .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

            val eventsService = OwnIdInternalEventsService(configuration, correlationId, okHttpClient)

            OwnIdInternalLogger.init(instanceName, eventsService)

            val appContext = context.applicationContext

            val localeService = OwnIdLocaleService(appContext, configuration, okHttpClient)

            val storageService = OwnIdStorageService(appContext, configuration.appId)

            val configurationService = OwnIdConfigurationService(configuration, localeService, storageService, appContext, okHttpClient)
            configurationService.ensureConfigurationSet {}

            return OwnIdCoreImpl(
                instanceName, configuration, correlationId, okHttpClient, eventsService, localeService, storageService, configurationService
            )
        }
    }

    override fun createWebViewBridge(): OwnIdWebViewBridge = OwnIdWebViewBridgeImpl(instanceName)
}