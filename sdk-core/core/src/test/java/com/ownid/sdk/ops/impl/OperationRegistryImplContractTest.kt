package com.ownid.sdk.ops.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.ErrorCode
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationFailure
import com.ownid.sdk.ops.OperationResult
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: SPI-090
internal class OperationRegistryImplContractTest {

    @Test
    fun registerAndUnregisterPublishCurrentActiveControllerSnapshots() {
        val registry = OperationRegistryImpl()
        val first = TestController(OperationID(OperationType.EMAIL_VERIFICATION, "first"))
        val second = TestController(OperationID(OperationType.PHONE_NUMBER_VERIFICATION, "second"))

        assertThat(registry.current.value).isEmpty()

        registry.register(first)
        val firstSnapshot = registry.current.value

        assertThat(firstSnapshot).containsExactly(first.operationId, first)

        registry.register(second)

        assertThat(registry.current.value).containsExactly(
            first.operationId, first,
            second.operationId, second
        )
        assertThat(firstSnapshot).containsExactly(first.operationId, first)

        registry.unregister(first.operationId)

        assertThat(registry.current.value).containsExactly(second.operationId, second)

        registry.unregister(OperationID(OperationType.WEB_BRIDGE, "missing"))

        assertThat(registry.current.value).containsExactly(second.operationId, second)

        registry.unregister(second.operationId)

        assertThat(registry.current.value).isEmpty()
    }

    private class TestController(
        override val operationId: OperationID
    ) : OperationController<Unit, TestFailure> {
        override suspend fun whenSettled(): OperationResult<Unit, TestFailure> =
            error("OperationRegistry tests do not assert terminal operation semantics")

        override fun abort(reason: Reason): Unit = Unit
    }

    private object TestFailure : OperationFailure {
        override val errorCode: ErrorCode = ErrorCode.Unknown
        override val message: String = "unused"
    }
}
