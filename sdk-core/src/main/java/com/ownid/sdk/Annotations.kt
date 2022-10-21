package com.ownid.sdk

/**
 * APIs marked with this annotation are OwnID internal and are not intended to be used outside.
 * They could be modified or removed without notice. Using them outside of OwnID could cause undefined behaviour and/or
 * undesirable effects.
 *
 * It is strongly recommended that these APIs not be used.
 */
@Retention(AnnotationRetention.BINARY)
@androidx.annotation.RequiresOptIn
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FILE,
    AnnotationTarget.TYPEALIAS
)
public annotation class InternalOwnIdAPI