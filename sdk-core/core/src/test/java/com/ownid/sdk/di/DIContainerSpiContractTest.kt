package com.ownid.sdk.di

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.model.InternalOwnIdAPI
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: SPI-010, SPI-020, SPI-030, SPI-040
internal class DIContainerSpiContractTest {

    @Test
    fun registeredInstancesAndFactoriesResolve() {
        val container = DIContainerImpl(scopeName = "test")
        val dependency = FakeDependency("registered")
        var factoryCalls = 0

        container.register(dependency)
        container.registerFactory<FakeProduct>(FakeDependency::class.java) {
            FakeProduct(dependency = getOrThrow(), sequence = ++factoryCalls)
        }

        assertThat(container.getOrThrow<FakeDependency>()).isSameInstanceAs(dependency)
        assertThat(container.canResolve(FakeProduct::class.java)).isTrue()
        assertThat(container.getUnsatisfiedDependencies(FakeProduct::class.java)).isNull()

        val first = container.getOrThrow<FakeProduct>()
        val second = container.getOrThrow<FakeProduct>()

        assertThat(first.dependency).isSameInstanceAs(dependency)
        assertThat(second.dependency).isSameInstanceAs(dependency)
        assertThat(first.sequence).isEqualTo(1)
        assertThat(second.sequence).isEqualTo(2)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun lastRegistrationWinsInSameScopeWithoutMutatingParent() {
        val parent = DIContainerImpl(scopeName = "parent")
        val parentService = FakeService("parent")
        val firstChildService = FakeService("first-child")
        val secondChildService = FakeService("second-child")

        parent.register<FakeContract>(parentService)
        val child = parent.createScope(scopeName = "child")

        assertThat(child.getOrThrow<FakeContract>()).isSameInstanceAs(parentService)

        child.register<FakeContract>(firstChildService)
        child.register<FakeContract>(secondChildService)

        assertThat(child.getOrThrow<FakeContract>()).isSameInstanceAs(secondChildService)
        assertThat(parent.getOrThrow<FakeContract>()).isSameInstanceAs(parentService)

        child.remove<FakeContract>()

        assertThat(child.getOrThrow<FakeContract>()).isSameInstanceAs(parentService)
        assertThat(parent.getOrThrow<FakeContract>()).isSameInstanceAs(parentService)
    }

    @Test
    fun parentFactoryResolvesNestedDependenciesFromOriginalRequester() {
        val parent = DIContainerImpl(scopeName = "parent")
        val child = parent.createScope(scopeName = "child")

        parent.register(FakeDependency("parent"))
        parent.registerFactory<FakeProduct>(FakeDependency::class.java) {
            FakeProduct(dependency = getOrThrow(), sequence = 1)
        }
        child.register(FakeDependency("child"))

        val resolved = child.getOrThrow<FakeProduct>()

        assertThat(resolved.dependency.name).isEqualTo("child")
        assertThat(parent.getOrThrow<FakeDependency>().name).isEqualTo("parent")
    }

    @Test
    fun missingDependencyReportsFailureCategoryAndTrace() {
        val container = DIContainerImpl(scopeName = "missing-scope")

        val missing = assertThrows(MissingDependencyException::class.java) {
            container.getOrThrow<MissingDependency>()
        }

        assertThat(missing.dependencyName).isEqualTo(MissingDependency::class.java.name)
        assertThat(missing.scopeName).isEqualTo("missing-scope")
        assertThat(missing.entryPoint).isEqualTo(MissingDependency::class.java.name)
        assertThat(missing).isInstanceOf(IllegalStateException::class.java)
        assertThat(container.getOrNull<MissingDependency>()).isNull()
        assertThat(container.canResolve(MissingDependency::class.java)).isFalse()
        assertUnsatisfiedTraceContains(
            container.getUnsatisfiedDependencies(MissingDependency::class.java),
            "missing-scope",
            MissingDependency::class.java.name
        )
    }

    @Test
    fun declaredMissingFactoryDependencyMakesRegisteredDependencyUnresolvable() {
        val container = DIContainerImpl(scopeName = "factory-scope")

        container.registerFactory<ProductWithMissingDependency>(MissingDependency::class.java) {
            ProductWithMissingDependency(getOrThrow())
        }

        assertThat(container.canResolve(ProductWithMissingDependency::class.java)).isFalse()
        assertUnsatisfiedTraceContains(
            container.getUnsatisfiedDependencies(ProductWithMissingDependency::class.java),
            "factory-scope",
            ProductWithMissingDependency::class.java.name,
            MissingDependency::class.java.name
        )

        val missing = assertThrows(MissingDependencyException::class.java) {
            container.getOrThrow<ProductWithMissingDependency>()
        }

        assertThat(missing.dependencyName).isEqualTo(MissingDependency::class.java.name)
        assertThat(missing.scopeName).isEqualTo("factory-scope")
        assertThat(missing.entryPoint).isEqualTo(ProductWithMissingDependency::class.java.name)
    }

    @Test
    fun declaredCycleIsReportedAndRuntimeCycleFailsAsResolutionException() {
        val container = DIContainerImpl(scopeName = "cycle-scope")

        container.registerFactory<CycleA>(CycleB::class.java) {
            CycleA(getOrThrow())
        }
        container.registerFactory<CycleB>(CycleA::class.java) {
            CycleB(getOrThrow())
        }

        assertThat(container.canResolve(CycleA::class.java)).isFalse()
        assertUnsatisfiedTraceContains(
            container.getUnsatisfiedDependencies(CycleA::class.java),
            "cycle-scope",
            CycleA::class.java.name,
            CycleB::class.java.name
        )

        val failure = assertThrows(DependencyResolutionException::class.java) {
            container.getOrThrow<CycleA>()
        }

        assertThat(failure.dependencyName).isEqualTo(CycleA::class.java.name)
        assertThat(failure.scopeName).isEqualTo("cycle-scope")
        assertThat(failure.entryPoint).isEqualTo(CycleA::class.java.name)
        assertThat(failure.cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(container.getOrNull<CycleA>()).isNull()
    }

    @Test
    fun getAllInstancesOfReturnsVisibleAssignableBindingsAndSkipsFailures() {
        val parent = DIContainerImpl(scopeName = "parent")
        val child = parent.createScope(scopeName = "child")
        val parentPlugin = ParentPlugin("parent")
        val childPlugin = ChildPlugin("child")

        parent.register(parentPlugin)
        parent.registerFactory<BrokenPlugin>(MissingDependency::class.java) {
            BrokenPlugin(getOrThrow())
        }
        child.register(childPlugin)

        val plugins = child.getAllInstancesOf(FakePlugin::class.java)

        assertThat(plugins).containsExactly(parentPlugin, childPlugin)
    }

    private interface FakeContract

    private data class FakeService(val name: String) : FakeContract

    private data class FakeDependency(val name: String)

    private data class FakeProduct(val dependency: FakeDependency, val sequence: Int)

    private class MissingDependency

    private data class ProductWithMissingDependency(val missingDependency: MissingDependency)

    private data class CycleA(val cycleB: CycleB)

    private data class CycleB(val cycleA: CycleA)

    private interface FakePlugin

    private data class ParentPlugin(val name: String) : FakePlugin

    private data class ChildPlugin(val name: String) : FakePlugin

    private data class BrokenPlugin(val missingDependency: MissingDependency) : FakePlugin

    private fun assertUnsatisfiedTraceContains(traces: List<String>?, vararg expectedFragments: String) {
        assertThat(traces).isNotNull()
        val actualTraces = traces.orEmpty()
        assertWithMessage("Expected an unsatisfied dependency trace containing ${expectedFragments.toList()}, got $actualTraces")
            .that(actualTraces.any { trace -> expectedFragments.all { fragment -> trace.contains(fragment) } })
            .isTrue()
    }
}
