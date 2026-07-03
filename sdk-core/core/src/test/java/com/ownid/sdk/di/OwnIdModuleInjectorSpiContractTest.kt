package com.ownid.sdk.di

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.InternalOwnIdAPI
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: SPI-050
internal class OwnIdModuleInjectorSpiContractTest {

    @Test
    fun moduleRegistrationIsScopedToProvidedContainer() {
        val root = DIContainerImpl(scopeName = "root")
        val moduleScope = root.createScope(scopeName = "module")
        val siblingScope = root.createScope(scopeName = "sibling")
        root.register<MarkerProbe>(RootMarker)

        MarkerModule.injectIntoInstanceContainer(moduleScope)

        assertThat(moduleScope.getOrThrow<MarkerProbe>()).isSameInstanceAs(ModuleMarker)
        assertThat(moduleScope.getOrThrow<ModuleOwnedMarker>()).isSameInstanceAs(ModuleOwnedMarker)
        assertThat(root.getOrThrow<MarkerProbe>()).isSameInstanceAs(RootMarker)
        assertThat(root.canResolve(ModuleOwnedMarker::class.java)).isFalse()
        assertThat(siblingScope.getOrThrow<MarkerProbe>()).isSameInstanceAs(RootMarker)
        assertThat(siblingScope.canResolve(ModuleOwnedMarker::class.java)).isFalse()
    }

    private interface MarkerProbe

    private object RootMarker : MarkerProbe

    private object ModuleMarker : MarkerProbe

    private object ModuleOwnedMarker

    private object MarkerModule : OwnIdModuleInjector.Module {
        override fun injectIntoInstanceContainer(container: DIContainer) {
            container.register<MarkerProbe>(ModuleMarker)
            container.register(ModuleOwnedMarker)
        }
    }
}
