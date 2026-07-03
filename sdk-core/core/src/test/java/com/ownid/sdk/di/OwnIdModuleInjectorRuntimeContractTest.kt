package com.ownid.sdk.di

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ops.ui.EmailVerificationUi
import com.ownid.sdk.ops.ui.LoginIdCollectUi
import com.ownid.sdk.ops.ui.PhoneVerificationUi
import com.ownid.sdk.ui.capability.OperationUiContainer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.InvocationTargetException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
// Covers: UI-PRESENT-050
internal class OwnIdModuleInjectorRuntimeContractTest {

    private val instancesToDestroy = mutableListOf<InstanceName>()
    private var restoredServices = true
    private var packageInfoServicesSnapshot: Array<ServiceInfo>? = null
    private val installedServiceNames = mutableListOf<ComponentName>()

    @After
    fun tearDown() {
        instancesToDestroy.forEach(OwnId::destroy)
        restorePackageServices()
        OwnId.logger {
            level = OwnIdLogger.Level.Off
        }
    }

    @Test
    fun badOptionalUiModuleMetadataIsLoggedSkippedAndCoreDefaultsRemainUsable() {
        val logEntries = mutableListOf<LogEntry>()
        OwnId.logger {
            level = OwnIdLogger.Level.Warn
            tag = "OwnID-Module-Test"
            log { level, className, message, cause ->
                logEntries += LogEntry(level, className, message, cause)
            }
        }

        val context = rootContextOrRuntimeApplication()
        val missingModuleClassName = "com.ownid.sdk.di.DoesNotExistOwnIdModule"
        val throwingConstructorModuleClassName = ThrowingConstructorOwnIdModule::class.java.name
        val throwingInjectionModuleClassName = ThrowingInjectionOwnIdModule::class.java.name
        val injectorClassNames = listOf(missingModuleClassName, throwingConstructorModuleClassName, throwingInjectionModuleClassName)
        installOptionalUiModuleMetadata(
            context = context,
            injectorClassNames = injectorClassNames
        )
        assertThat(runtimeInjectorMetadata(context)).containsExactlyElementsIn(injectorClassNames).inOrder()

        val instanceName = uniqueInstanceName()
        OwnId.initialize(instanceName) {
            applicationContext = RuntimeEnvironment.getApplication()
            appId = "BadOptionalUiModuleApp123"
        }

        val container = requireNotNull(OwnId.getInstanceContainer(instanceName))
        assertThat(OwnId.instance(instanceName).configuration.appId).isEqualTo("BadOptionalUiModuleApp123")
        assertThat(container.getOrThrow<LocalInfo>().packageName).isEqualTo(context.packageName)
        assertThat(container.canResolve(OperationUiContainer::class.java)).isFalse()
        assertThat(container.getOrThrow<LoginIdCollectUi>()).isNotNull()
        assertThat(container.getOrThrow<EmailVerificationUi>()).isNotNull()
        assertThat(container.getOrThrow<PhoneVerificationUi>()).isNotNull()

        logEntries.singleInstantiationWarningFor(missingModuleClassName).also { log ->
            assertThat(log.className).contains("OwnIdModuleInjector")
            assertThat(log.cause).isInstanceOf(ClassNotFoundException::class.java)
        }

        logEntries.singleInstantiationWarningFor(throwingConstructorModuleClassName).also { log ->
            assertThat(log.className).contains("OwnIdModuleInjector")
            val constructorFailure = log.cause as InvocationTargetException
            assertThat(constructorFailure.cause).isInstanceOf(IllegalStateException::class.java)
            assertThat(constructorFailure.cause).hasMessageThat().isEqualTo("optional module constructor failed")
        }

        logEntries.singleInjectionWarningFor(throwingInjectionModuleClassName).also { log ->
            assertThat(log.className).contains("injectIntoInstanceContainer")
            assertThat(log.cause).isInstanceOf(IllegalStateException::class.java)
            assertThat(log.cause).hasMessageThat().isEqualTo("optional module injection failed")
        }
    }

    private fun installOptionalUiModuleMetadata(context: Context, injectorClassNames: List<String>) {
        val packageManager = shadowOf(context.packageManager)
        val packageInfo = packageManager.getInternalMutablePackageInfo(context.packageName)
        packageInfoServicesSnapshot = packageInfo.services
        restoredServices = false
        val syntheticServices = injectorClassNames.mapIndexed { index, injectorClassName ->
            ServiceInfo().apply {
                packageName = context.packageName
                name = "${context.packageName}.OptionalUiInjectorMetadataHolder$index"
                applicationInfo = ApplicationInfo(context.applicationInfo)
                enabled = true
                exported = false
                metaData = Bundle().apply {
                    putString(INJECTOR_KEY, injectorClassName)
                }
            }
        }
        packageInfo.services = (packageInfo.services?.filterNotNull().orEmpty() + syntheticServices).toTypedArray()
        syntheticServices.forEach { serviceInfo ->
            val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
            installedServiceNames += componentName
            packageManager.addOrUpdateService(serviceInfo)
        }
    }

    private fun restorePackageServices() {
        if (restoredServices) return
        val context = rootContextOrRuntimeApplication()
        val packageManager = shadowOf(context.packageManager)
        installedServiceNames.forEach(packageManager::removeService)
        installedServiceNames.clear()
        packageManager.getInternalMutablePackageInfo(context.packageName).services = packageInfoServicesSnapshot
        restoredServices = true
    }

    @Suppress("DEPRECATION")
    private fun runtimeInjectorMetadata(context: Context): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)
            .services
            .orEmpty()
            .mapNotNull { it.metaData?.getString(INJECTOR_KEY) }

    private fun rootContextOrRuntimeApplication(): Context =
        OwnIdRootDIContainer.getOrNull<Context>() ?: RuntimeEnvironment.getApplication()

    private fun uniqueInstanceName(): InstanceName =
        InstanceName("module-injector-runtime-${UUID.randomUUID()}").also { instancesToDestroy += it }

    private fun List<LogEntry>.singleInstantiationWarningFor(moduleClassName: String): LogEntry =
        singleWarningFor("Failed to instantiate injector: $moduleClassName")

    private fun List<LogEntry>.singleInjectionWarningFor(moduleClassName: String): LogEntry =
        singleWarningFor("Failed for: class $moduleClassName")

    private fun List<LogEntry>.singleWarningFor(message: String): LogEntry {
        val matches = filter { it.level == OwnIdLogger.Level.Warn && it.message == message }
        assertWithMessage("Captured warning messages: ${filter { it.level == OwnIdLogger.Level.Warn }.map { it.message }}")
            .that(matches)
            .hasSize(1)
        return matches.single()
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )

    private companion object {
        private const val INJECTOR_KEY = "com.ownid.sdk.ui.INJECTOR_KEY"
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class ThrowingConstructorOwnIdModule : OwnIdModuleInjector.Module {
    init {
        throw IllegalStateException("optional module constructor failed")
    }

    override fun injectIntoInstanceContainer(container: DIContainer) = Unit
}

@OptIn(InternalOwnIdAPI::class)
internal class ThrowingInjectionOwnIdModule : OwnIdModuleInjector.Module {
    override fun injectIntoInstanceContainer(container: DIContainer) {
        throw IllegalStateException("optional module injection failed")
    }
}
