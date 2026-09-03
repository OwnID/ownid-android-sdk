package com.ownid.sdk.di

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
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
// Covers: SPI-100, UI-PRESENT-050
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
    fun recoverableOptionalUiModuleFailuresAreLoggedSkippedAndCoreDefaultsRemainUsable() {
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
        val directLinkageErrorModuleClassName = DirectLinkageErrorOwnIdModule::class.java.name
        val validModuleClassName = ValidOwnIdModule::class.java.name
        val injectorClassNames = listOf(
            missingModuleClassName,
            throwingConstructorModuleClassName,
            throwingInjectionModuleClassName,
            directLinkageErrorModuleClassName,
            STATIC_INITIALIZER_FAILURE_MODULE_CLASS_NAME,
            STATIC_INITIALIZER_FAILURE_MODULE_CLASS_NAME,
            validModuleClassName
        )
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
        assertThat(container.getOrThrow<ValidModuleMarker>()).isSameInstanceAs(ValidModuleMarker)

        val warnings = logEntries.filter {
            it.level == OwnIdLogger.Level.Warn &&
                (it.className.contains("OwnIdModuleInjector") || it.className.endsWith(":injectIntoInstanceContainer"))
        }
        assertThat(warnings).hasSize(6)

        warnings.single { it.cause is ClassNotFoundException }.also { log ->
            assertThat(log.className).contains("OwnIdModuleInjector")
            assertThat(log.message).contains(missingModuleClassName)
            assertThat(log.cause).isInstanceOf(ClassNotFoundException::class.java)
        }

        warnings.single { it.cause is InvocationTargetException }.also { log ->
            assertThat(log.className).contains("OwnIdModuleInjector")
            assertThat(log.message).contains(throwingConstructorModuleClassName)
            val constructorFailure = log.cause as InvocationTargetException
            assertThat(constructorFailure.cause).isInstanceOf(IllegalStateException::class.java)
            assertThat(constructorFailure.cause).hasMessageThat().isEqualTo("optional module constructor failed")
        }

        warnings.single { it.cause is IllegalStateException }.also { log ->
            assertThat(log.className).contains("injectIntoInstanceContainer")
            assertThat(log.message).contains(throwingInjectionModuleClassName)
            assertThat(log.cause).isInstanceOf(IllegalStateException::class.java)
            assertThat(log.cause).hasMessageThat().isEqualTo("optional module injection failed")
        }

        warnings.single { it.cause is NoSuchMethodError }.also { log ->
            assertThat(log.message).contains(directLinkageErrorModuleClassName)
            assertThat(log.cause).isInstanceOf(NoSuchMethodError::class.java)
            assertThat(log.cause).hasMessageThat().isEqualTo("optional module linked injection failed")
        }

        warnings.filter { it.cause is ExceptionInInitializerError || it.cause is NoClassDefFoundError }.also { logs ->
            assertThat(logs).hasSize(2)
            assertThat(logs.all { it.message.contains(STATIC_INITIALIZER_FAILURE_MODULE_CLASS_NAME) }).isTrue()
            assertThat(logs[0].cause).isInstanceOf(ExceptionInInitializerError::class.java)
            assertThat(logs[0].cause?.cause).isInstanceOf(IllegalStateException::class.java)
            assertThat(logs[0].cause?.cause).hasMessageThat().isEqualTo("optional module static initializer failed")
            assertThat(logs[1].cause).isInstanceOf(NoClassDefFoundError::class.java)
        }
        assertThat(warnings.none { it.message.contains(validModuleClassName) }).isTrue()
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

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )

    private companion object {
        private const val INJECTOR_KEY = "com.ownid.sdk.ui.INJECTOR_KEY"
        private const val STATIC_INITIALIZER_FAILURE_MODULE_CLASS_NAME =
            "com.ownid.sdk.di.StaticInitializerFailureOwnIdModule"
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

@OptIn(InternalOwnIdAPI::class)
internal class DirectLinkageErrorOwnIdModule : OwnIdModuleInjector.Module {
    override fun injectIntoInstanceContainer(container: DIContainer) {
        throw NoSuchMethodError("optional module linked injection failed")
    }
}

@OptIn(InternalOwnIdAPI::class)
internal class StaticInitializerFailureOwnIdModule : OwnIdModuleInjector.Module {
    companion object {
        init {
            throw IllegalStateException("optional module static initializer failed")
        }
    }

    override fun injectIntoInstanceContainer(container: DIContainer) = Unit
}

internal object ValidModuleMarker

@OptIn(InternalOwnIdAPI::class)
internal class ValidOwnIdModule : OwnIdModuleInjector.Module {
    override fun injectIntoInstanceContainer(container: DIContainer) {
        container.register(ValidModuleMarker)
    }
}
