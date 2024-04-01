package com.ownid.sdk

/**
 * Type alias for OwnID SDK callback.
 * Called when operation completed with a [Result] value.
 * **Important:** Always called on Main thread.
 */
public typealias OwnIdCallback<T> = Result<T>.() -> Unit

/**
 * Type alias for OwnID SDK product name.
 * Product name is used in network request as a part of User Agent.
 */
public typealias ProductName = String

/**
 * Type alias for OwnID SDK login id (e.g., email or phone number).
 */
public typealias LoginId = String