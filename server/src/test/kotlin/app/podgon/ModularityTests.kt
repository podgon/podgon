package app.podgon

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/** Проверка границ модулей (ADR-001): зависимости только через публичное API модулей. */
class ModularityTests {
    @Test
    fun `структура модулей корректна`() {
        ApplicationModules.of(PodgonApplication::class.java).verify()
    }
}
