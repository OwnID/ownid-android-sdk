package com.ownid.sdk

/**
 * APIs marked with this annotation are OwnID internal and are not intended to be used outside.
 * They could be modified or removed without notice. Using them outside of OwnID could cause undefined behaviour and/or
 * undesirable effects.
 *
 * It is strongly recommended that these APIs not be used.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in OwnID and should not be used. It could be removed or changed without notice."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER
)
public annotation class InternalOwnIdAPI