package com.ownid.sdk.model

/**
 * Describes whether an SDK action can start with the current input and runtime state.
 *
 * When the action cannot start, [Unavailable.message] contains a human-readable explanation that can be logged
 * while integrating the SDK. Do not branch product logic on exact message text.
 */
public sealed class Availability {
    public class Available : Availability()

    /**
     * The action cannot start with the provided input.
     * The message is human-readable diagnostic text. The SDK does not guarantee stable wording.
     */
    public class Unavailable(public val message: String) : Availability()
}

/**
 * Invokes [action] if this availability is [Availability.Available].
 *
 * Returns the original availability unchanged for fluent chaining.
 */
public inline fun Availability.onAvailable(action: () -> Unit): Availability {
    if (this is Availability.Available) action()
    return this
}

/**
 * Invokes [action] if this availability is [Availability.Unavailable].
 *
 * The action receives [Availability.Unavailable.message], which is diagnostic text for integration and logging, not a
 * stable product decision value.
 *
 * Returns the original availability unchanged for fluent chaining.
 */
public inline fun Availability.onUnavailable(action: (String) -> Unit): Availability {
    if (this is Availability.Unavailable) action(message)
    return this
}
