package app.podgon.groups.web

import app.podgon.groups.domain.notFound
import java.util.UUID

/** Идентификатор из адреса запроса; нераспознанный считается несуществующим ресурсом (404). */
fun String.toUuidOrNotFound(): UUID = runCatching { UUID.fromString(this) }.getOrNull() ?: throw notFound()
