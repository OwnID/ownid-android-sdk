package com.ownid.sdk

/**
 * Holds name of OwnID instance
 */
public class InstanceName(public val value: String) {

    override fun toString(): String = "InstanceName(`$value`)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InstanceName
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int = value.hashCode()
}