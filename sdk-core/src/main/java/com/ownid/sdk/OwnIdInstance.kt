package com.ownid.sdk

/**
 * General high level interface for any OwnID instance.
 */
public interface OwnIdInstance {

    /**
     * Name of OwnID instance. Must be unique for Android application.
     */
    public val instanceName: InstanceName
}