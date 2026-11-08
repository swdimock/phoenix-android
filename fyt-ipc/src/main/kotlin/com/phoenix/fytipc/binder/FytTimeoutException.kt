package com.phoenix.fytipc.binder

/**
 * Thrown by [com.phoenix.fytipc.FytModule.cmd] / [com.phoenix.fytipc.FytModule.get] when the toolkit
 * service did not connect within the connect timeout (10 s by default). Deliberately not a
 * `CancellationException`, so it surfaces instead of silently cancelling the caller.
 */
class FytTimeoutException(message: String) : IllegalStateException(message)
