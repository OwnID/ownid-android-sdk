package com.ownid.sdk.exception

/**
 * Exception that occurs when user cancelled any OwnID flow.
 * Usually occurs when user cancels Custom Tab operation.
 * It also can happen when OwnID SDK has wrong redirect uri set.
 */
public class FlowCanceled : OwnIdException("User canceled OwnID flow")