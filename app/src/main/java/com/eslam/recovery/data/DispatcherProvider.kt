package com.eslam.recovery.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Injected instead of referencing [Dispatchers] directly so tests can substitute a test dispatcher. */
data class DispatcherProvider(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)
