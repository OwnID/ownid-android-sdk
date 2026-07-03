package com.ownid.sdk.model


/**
 * Marks an OwnID-internal SDK module contract.
 *
 * This is not a public app integration contract. Annotated declarations are reserved for SDK modules and may change or
 * be removed without notice. Opting in produces a compile-time error by default.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in OwnID and should not be used. It could be removed or changed without notice."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS
)
public annotation class InternalOwnIdAPI
