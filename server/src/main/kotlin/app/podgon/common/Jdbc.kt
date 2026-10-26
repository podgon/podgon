package app.podgon.common

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** pgjdbc не принимает [Instant] как параметр, поэтому передаём [Timestamp]. */
fun Instant.toSql(): Timestamp = Timestamp.from(this)

fun ResultSet.instant(column: String): Instant = getTimestamp(column).toInstant()

fun ResultSet.instantOrNull(column: String): Instant? = getTimestamp(column)?.toInstant()
