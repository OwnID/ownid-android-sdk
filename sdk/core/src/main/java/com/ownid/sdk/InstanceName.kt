package com.ownid.sdk

/**
 * Holds name of OwnID instance
 */
public class InstanceName(private val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        else -> value == (other as InstanceName).value
    }

    override fun hashCode(): Int = value.hashCode()

    public companion object {
        public val DEFAULT: InstanceName = InstanceName("DefaultInstance")
    }
}