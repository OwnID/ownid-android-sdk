package com.ownid.sdk.di

import android.content.Context
import android.os.Build
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.analytics.UserJourney
import com.ownid.sdk.analytics.impl.UserJourneyImpl
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.DiscoverApi
import com.ownid.sdk.api.EmailEnrollApi
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.LoginApi
import com.ownid.sdk.api.OidcApi
import com.ownid.sdk.api.PasskeyAssertionApi
import com.ownid.sdk.api.PasskeyAttestationApi
import com.ownid.sdk.api.PasskeyEnrollApi
import com.ownid.sdk.api.PhoneEnrollApi
import com.ownid.sdk.api.PhoneVerificationApi
import com.ownid.sdk.api.impl.ApiBaseUrlImpl
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.ApiCallPipelineInterceptor
import com.ownid.sdk.api.impl.DefaultApiFailLoggingInterceptor
import com.ownid.sdk.api.impl.enroll.EmailEnrollApiImpl
import com.ownid.sdk.api.impl.enroll.PasskeyEnrollApiImpl
import com.ownid.sdk.api.impl.enroll.PhoneEnrollApiImpl
import com.ownid.sdk.api.impl.events.EventsApiImpl
import com.ownid.sdk.api.impl.login.DiscoverApiImpl
import com.ownid.sdk.api.impl.login.LoginApiImpl
import com.ownid.sdk.api.impl.oidc.OidcApiImpl
import com.ownid.sdk.api.impl.passkey.PasskeyAssertionApiImpl
import com.ownid.sdk.api.impl.passkey.PasskeyAttestationApiImpl
import com.ownid.sdk.api.impl.verification.EmailVerificationApiImpl
import com.ownid.sdk.api.impl.verification.PhoneVerificationApiImpl
import com.ownid.sdk.api.internal.EventsApi
import com.ownid.sdk.appUrlHeaderValue
import com.ownid.sdk.capability.ActivityProvider
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.PasskeyDiagnostics
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.capability.impl.AppConfigProviderImpl
import com.ownid.sdk.capability.impl.DefaultOkHttpClientFactory
import com.ownid.sdk.capability.impl.LoginIdConfigurationProviderImpl
import com.ownid.sdk.capability.impl.LoginIdValidatorImpl
import com.ownid.sdk.capability.impl.NetworkImpl
import com.ownid.sdk.capability.impl.PasskeyDiagnosticsImpl
import com.ownid.sdk.capability.impl.PasskeyImpl
import com.ownid.sdk.capability.impl.StorageImpl
import com.ownid.sdk.capability.impl.UserRepositoryImpl
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.flow.boost.BoostFlow
import com.ownid.sdk.flow.boost.impl.BoostCreatePasskeyFlowImpl
import com.ownid.sdk.flow.boost.impl.BoostLoginFlowImpl
import com.ownid.sdk.flow.elite.EliteFlow
import com.ownid.sdk.flow.elite.impl.EliteFlowImpl
import com.ownid.sdk.flow.passkey.PasskeyEnrollFlow
import com.ownid.sdk.flow.passkey.impl.PasskeyEnrollFlowImpl
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.model.AccessOrProofToken
import com.ownid.sdk.model.ErrorStrings
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.EmailVerificationOperation
import com.ownid.sdk.ops.EmailVerificationOperationFailure
import com.ownid.sdk.ops.LoginIdCollectOperation
import com.ownid.sdk.ops.LoginIdCollectOperationFailure
import com.ownid.sdk.ops.LoginOperation
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationRegistry
import com.ownid.sdk.ops.PasskeyAssertionOperation
import com.ownid.sdk.ops.PasskeyAttestationOperation
import com.ownid.sdk.ops.PasskeyEnrollOperation
import com.ownid.sdk.ops.PhoneVerificationOperation
import com.ownid.sdk.ops.PhoneVerificationOperationFailure
import com.ownid.sdk.ops.SignInWithGoogleOperation
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.impl.EmailVerificationOperationImpl
import com.ownid.sdk.ops.impl.LoginIdCollectOperationImpl
import com.ownid.sdk.ops.impl.LoginOperationImpl
import com.ownid.sdk.ops.impl.OperationRegistryImpl
import com.ownid.sdk.ops.impl.PasskeyAssertionOperationImpl
import com.ownid.sdk.ops.impl.PasskeyAttestationOperationImpl
import com.ownid.sdk.ops.impl.PasskeyEnrollOperationImpl
import com.ownid.sdk.ops.impl.PhoneVerificationOperationImpl
import com.ownid.sdk.ops.impl.SignInWithGoogleOperationImpl
import com.ownid.sdk.ops.impl.WebBridgeOperationImpl
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
import com.ownid.sdk.ops.ui.WebBridgeUi
import com.ownid.sdk.ops.ui.impl.PasskeyAssertionUiImpl
import com.ownid.sdk.ops.ui.impl.PasskeyAttestationUiImpl
import com.ownid.sdk.ops.ui.impl.SignInWithGoogleUiImpl
import com.ownid.sdk.ops.ui.impl.WebBridgeUiImpl
import com.ownid.sdk.storageFileName
import com.ownid.sdk.ui.OwnIdWebViewActivity
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import com.ownid.sdk.ui.capability.BoostWidgetStringsParams
import com.ownid.sdk.ui.capability.BoostWidgetStringsProvider
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.EmailVerificationStringsParams
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.ErrorStringsParams
import com.ownid.sdk.ui.capability.ErrorStringsProvider
import com.ownid.sdk.ui.capability.ErrorStringsResolver
import com.ownid.sdk.ui.capability.HiddenActivityLauncher
import com.ownid.sdk.ui.capability.LoginIdCollectStrings
import com.ownid.sdk.ui.capability.LoginIdCollectStringsParams
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStringsParams
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import com.ownid.sdk.ui.capability.impl.BoostWidgetStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.BoostWidgetStringsServerRepositoryEmptyFallback
import com.ownid.sdk.ui.capability.impl.BoostWidgetStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.EmailVerificationStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.EmailVerificationStringsServerRepositoryEmptyFallback
import com.ownid.sdk.ui.capability.impl.EmailVerificationStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.EmbeddedOnlyStringsProvider
import com.ownid.sdk.ui.capability.impl.ErrorStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.ErrorStringsServerRepositoryEmptyFallback
import com.ownid.sdk.ui.capability.impl.ErrorStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.HiddenActivityLauncherImpl
import com.ownid.sdk.ui.capability.impl.LoginIdCollectStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.LoginIdCollectStringsServerRepositoryEmptyFallback
import com.ownid.sdk.ui.capability.impl.LoginIdCollectStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.PhoneVerificationStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.PhoneVerificationStringsServerRepositoryEmptyFallback
import com.ownid.sdk.ui.capability.impl.PhoneVerificationStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.StringsProviderImpl
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import com.ownid.sdk.ui.locale.ServerLocaleDataSourceProviderImpl
import com.ownid.sdk.ui.webbridge.WebBridge
import com.ownid.sdk.ui.webbridge.WebBridgeContextPlugin
import com.ownid.sdk.ui.webbridge.WebBridgeElitePlugin
import com.ownid.sdk.ui.webbridge.WebBridgeMetadataPlugin
import com.ownid.sdk.ui.webbridge.WebBridgePasskeyPlugin
import com.ownid.sdk.ui.webbridge.WebBridgeSocialPlugin
import com.ownid.sdk.ui.webbridge.WebBridgeUserRepositoryPlugin
import com.ownid.sdk.ui.webbridge.impl.WebBridgeImpl
import com.ownid.sdk.ui.webbridge.impl.WebBridgePluginFactoryStoreImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeContextPluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeElitePluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeMetadataPluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgePasskeyPluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeSocialPluginImpl
import com.ownid.sdk.ui.webbridge.impl.plugins.WebBridgeUserRepositoryPluginImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Registers all default SDK capabilities, APIs, operations, and flows into this instance container.
 *
 * This is an internal SDK module contract, not a public app integration contract. Core owns the baseline instance
 * graph and the instance-wide parent [Job]. Long-lived services should use that job directly or create child jobs so
 * [OwnIdRootDIContainer.destroyInstanceContainer] and same-name reinitialization can cancel instance-owned work.
 *
 * Optional modules may add or replace module-owned extension bindings in this container. Core remains the owner of the
 * baseline runtime contracts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@InternalOwnIdAPI
internal fun DIContainer.injectInstanceDefaults(instanceName: InstanceName, configuration: OwnIdConfiguration) {
    val instanceContainer = this
    val logRouter = OwnIdLogRouter(
        ownIdLoggerProvider = { instanceContainer.getOrNull<OwnIdLogger>() },
        serverLoggerProvider = { instanceContainer.getOrNull<ServerLogger>() }
    )
    register<OwnIdLogRouter>(logRouter)
    (this as DIContainerImpl).setLogRouter(logRouter)

    register<InstanceName>(instanceName)

    register<OwnIdConfiguration>(configuration)

    register<Job>(SupervisorJob()) // Instance-global job. Canceled on instance destroy.

    register<DispatcherProvider>(
        @OptIn(ExperimentalCoroutinesApi::class)
        object : DispatcherProvider {
            override fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
            override fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
        }
    )

    // Keep one Storage owner per backing file to avoid concurrent writes through separate stores.
    runCatchingExceptions {
        StorageImpl(
            context = getOrThrow<Context>(),
            fileName = getOrThrow<OwnIdConfiguration>().storageFileName(),
            scope = CoroutineScope(getOrThrow<DispatcherProvider>().ioDispatcher() + SupervisorJob(getOrThrow<Job>())),
            logger = getOrNull<OwnIdLogRouter>()
        )
    }
        .onSuccess { register<Storage>(it) }
        .onFailure { getOrNull<OwnIdLogRouter>()?.logW(this, "injectInstanceDefaults", "Failed to create Storage: ${it.message}", it) }

    registerFactory<UserRepository>(Storage::class.java, Serialization::class.java) {
        UserRepositoryImpl(
            storage = getOrThrow<Storage>(),
            serialization = getOrThrow<Serialization>()
        )
    }

    register<LoginIdConfigurationProvider>(LoginIdConfigurationProviderImpl())

    registerFactory<LoginIdValidator>(LoginIdConfigurationProvider::class.java) {
        LoginIdValidatorImpl(loginIdConfigurationProvider = getOrThrow<LoginIdConfigurationProvider>())
    }

    register<OkHttpClient>(
        DefaultOkHttpClientFactory.create(
            localInfo = getOrNull<LocalInfo>(),
            languageTagsProvider = getOrNull<LanguageTagsProvider>(),
            appUrlHeaderValue = getOrThrow<OwnIdConfiguration>().appUrlHeaderValue(),
            ownIdLogger = getOrNull<OwnIdLogger>()
        )
    )

    registerFactory<Network.Request.RetryConfig> { Network.Request.RetryConfig.Default }

    registerFactory<Network>(OkHttpClient::class.java) {
        NetworkImpl(
            okHttpClient = getOrThrow<OkHttpClient>(),
            retryConfig = getOrNull<Network.Request.RetryConfig>() ?: Network.Request.RetryConfig.Default
        )
    }

    registerFactory<ApiCallInterceptor> {
        ApiCallPipelineInterceptor(
            interceptors = listOf(
                DefaultApiFailLoggingInterceptor(serverLoggerProvider = { getOrNull<ServerLogger>() })
            )
        )
    }

    registerFactory<ApiBaseUrl>(OwnIdConfiguration::class.java) {
        ApiBaseUrlImpl(configuration = getOrThrow<OwnIdConfiguration>())
    }

    runCatchingExceptions {
        AppConfigProviderImpl(
            context = getOrThrow<Context>(),
            configuration = getOrThrow<OwnIdConfiguration>(),
            apiBaseUrl = getOrThrow<ApiBaseUrl>(),
            okHttpClient = getOrThrow<OkHttpClient>(),
            serialization = getOrThrow<Serialization>(),
            dispatcherProvider = getOrThrow<DispatcherProvider>(),
            job = getOrThrow<Job>(),
            loginIdConfigurationProvider = getOrNull<LoginIdConfigurationProvider>(),
            logger = getOrNull<OwnIdLogRouter>(),
            interceptor = getOrNull<ApiCallInterceptor>()
        )
    }
        .onSuccess { register<AppConfigProvider>(it) }
        .onFailure { getOrNull<OwnIdLogRouter>()?.logW(this, "injectInstanceDefaults", "Failed to create ServerConfiguration: ${it.message}", it) }

    runCatchingExceptions {
        ServerLogger(
            instanceName = getOrThrow<InstanceName>(),
            configuration = getOrThrow<OwnIdConfiguration>(),
            localInfo = getOrThrow<LocalInfo>(),
            appConfigProvider = getOrThrow<AppConfigProvider>(),
            network = getOrThrow<Network>(),
            serialization = getOrThrow<Serialization>(),
            dispatcher = getOrThrow<DispatcherProvider>().ioDispatcher().limitedParallelism(2),
            instanceJob = getOrThrow<Job>(),
            ownIdLogger = getOrNull<OwnIdLogger>()
        )
    }
        .onSuccess { register<ServerLogger>(it) }
        .onFailure { getOrNull<OwnIdLogRouter>()?.logW(this, "injectInstanceDefaults", "Failed to create ServerLogger: ${it.message}", it) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatchingExceptions {
            PasskeyDiagnosticsImpl(
                context = getOrThrow<Context>(),
                localInfo = getOrThrow<LocalInfo>(),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                logger = getOrNull<OwnIdLogRouter>()
            )
        }.getOrElse {
            object : PasskeyDiagnostics {
                override fun verify(rpId: String) = Unit
            }
        }.also { register<PasskeyDiagnostics>(it) }

        registerFactory<Passkey>(Serialization::class.java) {
            runCatchingExceptions {
                PasskeyImpl(
                    serialization = getOrThrow<Serialization>(),
                    logger = getOrNull<OwnIdLogRouter>(),
                    passkeyDiagnosticsProvider = { getOrNull<PasskeyDiagnostics>() }
                )
            }.getOrElse(PasskeyImpl::failingPasskey)
        }
    }

    registerFactory<LoginApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        LoginApiImpl.create(this)
    }

    registerFactory<DiscoverApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        DiscoverApiImpl.create(this)
    }

    registerFactory<EmailVerificationApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        EmailVerificationApiImpl.create(this)
    }

    registerFactory<PhoneVerificationApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        PhoneVerificationApiImpl.create(this)
    }

    registerFactory<PasskeyAssertionApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        PasskeyAssertionApiImpl.create(this)
    }

    registerFactory<PasskeyAttestationApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        PasskeyAttestationApiImpl.create(this)
    }

    registerFactory<PasskeyEnrollApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        PasskeyEnrollApiImpl.create(this)
    }

    registerFactory<EmailEnrollApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        EmailEnrollApiImpl.create(this)
    }

    registerFactory<PhoneEnrollApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        PhoneEnrollApiImpl.create(this)
    }

    registerFactory<OidcApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        OidcApiImpl.create(this)
    }

    registerFactory<EventsApi>(ApiBaseUrl::class.java, Network::class.java, Serialization::class.java, DispatcherProvider::class.java) {
        EventsApiImpl.create(this)
    }

    runCatchingExceptions {
        ServerLocaleDataSourceProviderImpl(
            context = getOrThrow<Context>(),
            configuration = getOrThrow<OwnIdConfiguration>(),
            okHttpClient = getOrThrow<OkHttpClient>(),
            languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
            serialization = getOrThrow<Serialization>(),
            dispatcher = getOrThrow<DispatcherProvider>().ioDispatcher().limitedParallelism(4),
            instanceJob = getOrThrow<Job>(),
            logger = getOrNull<OwnIdLogRouter>()
        )
    }
        .onSuccess { register<ServerLocaleDataSource.Provider>(it) }
        .onFailure { getOrNull<OwnIdLogRouter>()?.logW(this, "injectInstanceDefaults", "Failed to create ServerLocaleDataSource.Provider: ${it.message}", it) }

    registerFactory<ErrorStringsProvider.EmbeddedRepository> { ErrorStringsEmbeddedRepositoryImpl() }

    registerFactory<ErrorStringsProvider.ServerRepository>(ServerLocaleDataSource.Provider::class.java) {
        runCatchingExceptions { ErrorStringsServerRepositoryImpl(serverLocaleProvider = getOrThrow<ServerLocaleDataSource.Provider>()) }
            .getOrDefault(ErrorStringsServerRepositoryEmptyFallback)

    }

    registerFactory<ErrorStringsProvider>(
        LanguageTagsProvider::class.java,
        ErrorStringsProvider.EmbeddedRepository::class.java,
        ErrorStringsProvider.ServerRepository::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        runCatchingExceptions {
            object : StringsProviderImpl<ErrorStrings, ErrorStringsParams>(
                languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
                embeddedRepository = getOrThrow<ErrorStringsProvider.EmbeddedRepository>(),
                serverRepository = getOrThrow<ErrorStringsProvider.ServerRepository>(),
                scope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
                logger = getOrNull<OwnIdLogRouter>()
            ), ErrorStringsProvider {}
        }.getOrDefault(
            object : EmbeddedOnlyStringsProvider<ErrorStrings, ErrorStringsParams>(
                embeddedRepository = getOrNull<ErrorStringsProvider.EmbeddedRepository>() ?: ErrorStringsEmbeddedRepositoryImpl(),
            ), ErrorStringsProvider {}
        )
    }

    runCatchingExceptions {
        ErrorStringsResolver(
            errorStringsProvider = getOrNull<ErrorStringsProvider>(),
            dispatcherProvider = getOrThrow<DispatcherProvider>(),
            instanceJob = getOrThrow<Job>()
        )
    }
        .onSuccess { register(it) }
        .onFailure { getOrNull<OwnIdLogRouter>()?.logW(this, "injectInstanceDefaults", "Failed to create ErrorStringsResolver: ${it.message}", it) }

    registerFactory<UiContextProvider>(Context::class.java) {
        UiContextProvider { getOrNull<ActivityProvider>()?.getActivity() ?: getOrThrow<Context>() }
    }

    registerFactory<HiddenActivityLauncher>(UiContextProvider::class.java) {
        HiddenActivityLauncherImpl(uiContextProvider = { getOrThrow<UiContextProvider>() }, logger = getOrNull<OwnIdLogRouter>())
    }

    register<OperationRegistry>(OperationRegistryImpl(logger = getOrNull<OwnIdLogRouter>()))

    registerFactory<LoginIdCollectStringsProvider.EmbeddedRepository>(LocalInfo::class.java) {
        LoginIdCollectStringsEmbeddedRepositoryImpl(localInfo = getOrNull<LocalInfo>())
    }

    registerFactory<LoginIdCollectStringsProvider.ServerRepository>(LocalInfo::class.java, ServerLocaleDataSource.Provider::class.java) {
        runCatchingExceptions {
            LoginIdCollectStringsServerRepositoryImpl(
                localInfo = getOrThrow<LocalInfo>(),
                serverLocaleProvider = getOrThrow<ServerLocaleDataSource.Provider>()
            )
        }.getOrDefault(LoginIdCollectStringsServerRepositoryEmptyFallback)

    }

    registerFactory<LoginIdCollectStringsProvider>(
        LanguageTagsProvider::class.java,
        LoginIdCollectStringsProvider.EmbeddedRepository::class.java,
        LoginIdCollectStringsProvider.ServerRepository::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        runCatchingExceptions {
            object : StringsProviderImpl<LoginIdCollectStrings, LoginIdCollectStringsParams>(
                languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
                embeddedRepository = getOrThrow<LoginIdCollectStringsProvider.EmbeddedRepository>(),
                serverRepository = getOrThrow<LoginIdCollectStringsProvider.ServerRepository>(),
                scope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
                logger = getOrNull<OwnIdLogRouter>()
            ), LoginIdCollectStringsProvider {}
        }.getOrDefault(
            object : EmbeddedOnlyStringsProvider<LoginIdCollectStrings, LoginIdCollectStringsParams>(
                embeddedRepository = getOrNull<LoginIdCollectStringsProvider.EmbeddedRepository>()
                    ?: LoginIdCollectStringsEmbeddedRepositoryImpl(localInfo = getOrNull<LocalInfo>())
            ), LoginIdCollectStringsProvider {}
        )
    }

    registerFactory<LoginIdCollectUi> {
        object : LoginIdCollectUi {
            override fun start(
                controller: OperationController<LoginId, LoginIdCollectOperationFailure>,
                onStartError: (error: Throwable?, details: String?) -> Unit
            ) {
                val details = "No UI implementation registered for LoginIdCollectUi. Add OwnID UI or register a custom LoginIdCollectUi."
                onStartError(IllegalStateException(details), details)
            }
        }
    }

    registerFactory<LoginIdCollectOperation>(
        OperationRegistry::class.java,
        LoginIdConfigurationProvider::class.java,
        LoginIdValidator::class.java,
        LoginIdCollectUi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        LoginIdCollectOperationImpl.create(this)
    }

    registerFactory<EmailVerificationStringsProvider.EmbeddedRepository> { EmailVerificationStringsEmbeddedRepositoryImpl() }

    registerFactory<EmailVerificationStringsProvider.ServerRepository>(ServerLocaleDataSource.Provider::class.java) {
        runCatchingExceptions { EmailVerificationStringsServerRepositoryImpl(serverLocaleProvider = getOrThrow<ServerLocaleDataSource.Provider>()) }
            .getOrDefault(EmailVerificationStringsServerRepositoryEmptyFallback)
    }

    registerFactory<EmailVerificationStringsProvider>(
        LanguageTagsProvider::class.java,
        EmailVerificationStringsProvider.EmbeddedRepository::class.java,
        EmailVerificationStringsProvider.ServerRepository::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        runCatchingExceptions {
            object : StringsProviderImpl<EmailVerificationStrings, EmailVerificationStringsParams>(
                languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
                embeddedRepository = getOrThrow<EmailVerificationStringsProvider.EmbeddedRepository>(),
                serverRepository = getOrThrow<EmailVerificationStringsProvider.ServerRepository>(),
                scope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
                logger = getOrNull<OwnIdLogRouter>()
            ), EmailVerificationStringsProvider {}
        }.getOrDefault(
            object : EmbeddedOnlyStringsProvider<EmailVerificationStrings, EmailVerificationStringsParams>(
                embeddedRepository = getOrNull<EmailVerificationStringsProvider.EmbeddedRepository>()
                    ?: EmailVerificationStringsEmbeddedRepositoryImpl(),
            ), EmailVerificationStringsProvider {}
        )
    }

    registerFactory<EmailVerificationUi> {
        object : EmailVerificationUi {
            override fun start(
                controller: OperationController<AccessOrProofToken, EmailVerificationOperationFailure>,
                onStartError: (error: Throwable?, details: String?) -> Unit
            ) {
                val details = "No UI implementation registered for EmailVerificationUi. Add OwnID UI or register a custom EmailVerificationUi."
                onStartError(IllegalStateException(details), details)
            }
        }
    }

    registerFactory<EmailVerificationOperation>(
        OperationRegistry::class.java,
        EmailVerificationUi::class.java,
        EmailVerificationApi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        EmailVerificationOperationImpl.create(this)
    }

    registerFactory<PhoneVerificationStringsProvider.EmbeddedRepository> { PhoneVerificationStringsEmbeddedRepositoryImpl() }

    registerFactory<PhoneVerificationStringsProvider.ServerRepository>(ServerLocaleDataSource.Provider::class.java) {
        runCatchingExceptions { PhoneVerificationStringsServerRepositoryImpl(serverLocaleProvider = getOrThrow<ServerLocaleDataSource.Provider>()) }
            .getOrDefault(PhoneVerificationStringsServerRepositoryEmptyFallback)
    }

    registerFactory<PhoneVerificationStringsProvider>(
        LanguageTagsProvider::class.java,
        PhoneVerificationStringsProvider.EmbeddedRepository::class.java,
        PhoneVerificationStringsProvider.ServerRepository::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        runCatchingExceptions {
            object : StringsProviderImpl<PhoneVerificationStrings, PhoneVerificationStringsParams>(
                languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
                embeddedRepository = getOrThrow<PhoneVerificationStringsProvider.EmbeddedRepository>(),
                serverRepository = getOrThrow<PhoneVerificationStringsProvider.ServerRepository>(),
                scope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
                logger = getOrNull<OwnIdLogRouter>()
            ), PhoneVerificationStringsProvider {}
        }.getOrDefault(
            object : EmbeddedOnlyStringsProvider<PhoneVerificationStrings, PhoneVerificationStringsParams>(
                embeddedRepository = getOrNull<PhoneVerificationStringsProvider.EmbeddedRepository>()
                    ?: PhoneVerificationStringsEmbeddedRepositoryImpl(),
            ), PhoneVerificationStringsProvider {}
        )
    }

    registerFactory<PhoneVerificationUi> {
        object : PhoneVerificationUi {
            override fun start(
                controller: OperationController<AccessOrProofToken, PhoneVerificationOperationFailure>,
                onStartError: (error: Throwable?, details: String?) -> Unit
            ) {
                val details = "No UI implementation registered for PhoneVerificationUi. Add OwnID UI or register a custom PhoneVerificationUi."
                onStartError(IllegalStateException(details), details)
            }
        }
    }

    registerFactory<PhoneVerificationOperation>(
        OperationRegistry::class.java,
        PhoneVerificationUi::class.java,
        PhoneVerificationApi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        PhoneVerificationOperationImpl.create(this)
    }

    registerFactory<PasskeyAttestationUi>(UiContextProvider::class.java, Passkey::class.java) {
        PasskeyAttestationUiImpl(
            uiContextProvider = { getOrThrow<UiContextProvider>() },
            passkeyProvider = { getOrThrow<Passkey>() }
        )
    }

    registerFactory<PasskeyAttestationOperation>(
        OperationRegistry::class.java,
        PasskeyAttestationUi::class.java,
        PasskeyAttestationApi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        PasskeyAttestationOperationImpl.create(this)
    }

    registerFactory<PasskeyAssertionUi>(UiContextProvider::class.java, Passkey::class.java) {
        PasskeyAssertionUiImpl(
            uiContextProvider = { getOrThrow<UiContextProvider>() },
            passkeyProvider = { getOrThrow<Passkey>() }
        )
    }

    registerFactory<PasskeyAssertionOperation>(
        OperationRegistry::class.java,
        PasskeyAssertionUi::class.java,
        PasskeyAssertionApi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        PasskeyAssertionOperationImpl.create(this)
    }

    registerFactory<PasskeyEnrollOperation>(OperationRegistry::class.java, PasskeyEnrollApi::class.java, DispatcherProvider::class.java, Job::class.java) {
        PasskeyEnrollOperationImpl.create(this)
    }

    registerFactory<SignInWithGoogleUi>(UiContextProvider::class.java, SignInWithGoogle::class.java) {
        SignInWithGoogleUiImpl(
            uiContextProvider = { getOrThrow<UiContextProvider>() },
            signInWithGoogleProvider = { getOrThrow<SignInWithGoogle>() }
        )
    }

    registerFactory<SignInWithGoogleOperation>(
        OperationRegistry::class.java,
        SignInWithGoogleUi::class.java,
        OidcApi::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        SignInWithGoogleOperationImpl.create(this)
    }

    registerFactory<LoginOperation>(
        OperationRegistry::class.java,
        LoginApi::class.java,
        DiscoverApi::class.java,
        LoginIdValidator::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        LoginOperationImpl.create(this)
    }

    registerFactory<WebBridgeUi.Container>(HiddenActivityLauncher::class.java) {
        object : WebBridgeUi.Container {
            override fun show(instanceName: InstanceName, operationID: OperationID, onShowError: (error: Throwable?, details: String?) -> Unit) {
                runCatchingExceptions { getOrThrow<HiddenActivityLauncher>() }
                    .onFailure { error -> onShowError(error, "Failed to get HiddenActivityLauncher: ${error.message}") }
                    .onSuccess { hiddenActivityLauncher ->
                        hiddenActivityLauncher.launchActivityForResult(
                            intentProvider = { context -> OwnIdWebViewActivity.createIntent(context, instanceName, operationID) },
                            onShowError = onShowError
                        )
                    }
            }
        }
    }

    registerFactory<WebBridgeUi>(WebBridgeUi.Container::class.java, InstanceName::class.java) {
        WebBridgeUiImpl { uid, onShowError -> getOrThrow<WebBridgeUi.Container>().show(getOrThrow<InstanceName>(), uid, onShowError) }
    }

    registerFactory<WebBridgeOperation>(
        OperationRegistry::class.java,
        OwnIdConfiguration::class.java,
        AppConfigProvider::class.java,
        LocalInfo::class.java,
        WebBridgeUi::class.java,
        WebBridge::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        WebBridgeOperationImpl.create(this)
    }

    registerFactory<BoostWidgetStringsProvider.EmbeddedRepository> {
        BoostWidgetStringsEmbeddedRepositoryImpl()
    }

    registerFactory<BoostWidgetStringsProvider.ServerRepository>(ServerLocaleDataSource.Provider::class.java) {
        runCatchingExceptions {
            BoostWidgetStringsServerRepositoryImpl(
                serverLocaleProvider = getOrThrow<ServerLocaleDataSource.Provider>()
            )
        }.getOrDefault(BoostWidgetStringsServerRepositoryEmptyFallback)
    }

    registerFactory<BoostWidgetStringsProvider>(
        LanguageTagsProvider::class.java,
        BoostWidgetStringsProvider.EmbeddedRepository::class.java,
        BoostWidgetStringsProvider.ServerRepository::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        runCatchingExceptions {
            object : StringsProviderImpl<BoostWidgetStrings, BoostWidgetStringsParams>(
                languageTagsProvider = getOrThrow<LanguageTagsProvider>(),
                embeddedRepository = getOrThrow<BoostWidgetStringsProvider.EmbeddedRepository>(),
                serverRepository = getOrThrow<BoostWidgetStringsProvider.ServerRepository>(),
                scope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
                logger = getOrNull<OwnIdLogRouter>()
            ), BoostWidgetStringsProvider {}
        }.getOrDefault(
            object : EmbeddedOnlyStringsProvider<BoostWidgetStrings, BoostWidgetStringsParams>(
                embeddedRepository = getOrNull<BoostWidgetStringsProvider.EmbeddedRepository>()
                    ?: BoostWidgetStringsEmbeddedRepositoryImpl(),
            ), BoostWidgetStringsProvider {}
        )
    }

    // The namespace copies this built-in template and applies namespace-local plugin overrides on top of it.

    val webBridgePluginFactoryStore = WebBridgePluginFactoryStoreImpl()
    register(webBridgePluginFactoryStore)

    registerFactory<WebBridgeUserRepositoryPlugin>(UserRepository::class.java, LoginIdValidator::class.java, Serialization::class.java) {
        WebBridgeUserRepositoryPluginImpl.create(this)
    }
    webBridgePluginFactoryStore.registerBuiltIn(WebBridgeUserRepositoryPlugin.KEY) { resolver ->
        resolver.getOrThrow(WebBridgeUserRepositoryPlugin::class.java)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        registerFactory<WebBridgePasskeyPlugin>(UiContextProvider::class.java, LocalInfo::class.java, Passkey::class.java, Serialization::class.java) {
            WebBridgePasskeyPluginImpl.create(this)
        }
        webBridgePluginFactoryStore.registerBuiltIn(WebBridgePasskeyPlugin.KEY) { resolver ->
            resolver.getOrThrow(WebBridgePasskeyPlugin::class.java)
        }
    }

    registerFactory<WebBridgeSocialPlugin>(UiContextProvider::class.java, Serialization::class.java) {
        WebBridgeSocialPluginImpl.create(this)
    }
    webBridgePluginFactoryStore.registerBuiltIn(WebBridgeSocialPlugin.KEY) { resolver ->
        resolver.getOrThrow(WebBridgeSocialPlugin::class.java)
    }

    registerFactory<WebBridgeMetadataPlugin>(LocalInfo::class.java) {
        WebBridgeMetadataPluginImpl.create(this)
    }
    webBridgePluginFactoryStore.registerBuiltIn(WebBridgeMetadataPlugin.KEY) { resolver ->
        resolver.getOrThrow(WebBridgeMetadataPlugin::class.java)
    }

    registerFactory<WebBridgeContextPlugin> {
        WebBridgeContextPluginImpl.create(this)
    }
    webBridgePluginFactoryStore.registerBuiltIn(WebBridgeContextPlugin.KEY) { resolver ->
        resolver.getOrThrow(WebBridgeContextPlugin::class.java)
    }

    registerFactory<WebBridgeElitePlugin>(Serialization::class.java, LoginIdValidator::class.java) {
        WebBridgeElitePluginImpl.create(this)
    }
    webBridgePluginFactoryStore.registerBuiltIn(WebBridgeElitePlugin.KEY) { resolver ->
        resolver.getOrThrow(WebBridgeElitePlugin::class.java)
    }

    // Direct DI resolution uses the current template; OwnIdWebBridge namespaces work with copies.

    registerFactory<WebBridge>(
        WebBridgePluginFactoryStoreImpl::class.java,
        AppConfigProvider::class.java,
        Serialization::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        WebBridgeImpl.create(resolver = this, initialPlugins = getOrThrow<WebBridgePluginFactoryStoreImpl>().instantiateAll(this))
    }

    registerFactory<UserJourney>(
        EventsApi::class.java,
        LocalInfo::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        UserJourneyImpl(
            eventsApi = getOrThrow<EventsApi>(),
            localInfo = getOrThrow<LocalInfo>(),
            userRepository = getOrNull<UserRepository>(),
            coroutineScope = CoroutineScope(getOrThrow<DispatcherProvider>().defaultDispatcher() + SupervisorJob(getOrThrow<Job>())),
            logger = getOrNull<OwnIdLogRouter>()
        )
    }

    registerFactory<BoostFlow.Login>(
        LoginIdValidator::class.java,
        Serialization::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        BoostLoginFlowImpl.create(this)
    }

    registerFactory<BoostFlow.CreatePasskey>(
        BoostFlow.Login::class.java,
        Serialization::class.java,
        LoginIdValidator::class.java,
        DispatcherProvider::class.java,
        Job::class.java
    ) {
        BoostCreatePasskeyFlowImpl.create(this)
    }

    registerFactory<EliteFlow>(DispatcherProvider::class.java, Job::class.java) {
        EliteFlowImpl.create(this)
    }

    registerFactory<PasskeyEnrollFlow>(Serialization::class.java, LoginIdValidator::class.java, DispatcherProvider::class.java, Job::class.java) {
        PasskeyEnrollFlowImpl.create(this)
    }

    // Optional modules may replace module-owned extension bindings while Core keeps ownership of baseline runtime contracts.
    OwnIdModuleInjector.injectIntoInstanceContainer(this)
}
