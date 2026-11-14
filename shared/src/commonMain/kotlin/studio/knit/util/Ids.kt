package studio.knit.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Random unique id for patterns, charts, rows and everything else that needs one. */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** Current time in epoch milliseconds. */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
