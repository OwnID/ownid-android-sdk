package com.ownid.sdk

/**
 * Holds name of OwnID instance
 */
public data class InstanceName(public val value: String) {

    override fun toString(): String = "InstanceName(`$value`)"
}