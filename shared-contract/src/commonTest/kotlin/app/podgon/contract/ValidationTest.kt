package app.podgon.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidationTest {
    @Test
    fun `email нормализуется и проверяется`() {
        assertEquals("a@b.ru", Validation.normalizeEmail("  A@B.ru "))
        assertNull(Validation.email("user@example.com"))
        assertEquals(FieldErrorReason.REQUIRED, Validation.email("  "))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.email("no-at.example.com"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.email("a@b"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.email("a b@c.ru"))
        assertEquals(FieldErrorReason.TOO_LONG, Validation.email("a".repeat(250) + "@b.ru"))
    }

    @Test
    fun `username - длина и символы и зарезервированный префикс`() {
        assertNull(Validation.username("Ivan_petrov.1"))
        assertEquals(FieldErrorReason.TOO_SHORT, Validation.username("ab"))
        assertEquals(FieldErrorReason.TOO_LONG, Validation.username("a".repeat(31)))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.username("иван"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.username("with space"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.username("a-b-c"))
        assertEquals(FieldErrorReason.RESERVED, Validation.username("Deleted_123"))
    }

    @Test
    fun `displayName и bio`() {
        assertNull(Validation.displayName("Иван Петров"))
        assertEquals(FieldErrorReason.REQUIRED, Validation.displayName("   "))
        assertEquals(FieldErrorReason.TOO_LONG, Validation.displayName("я".repeat(61)))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.displayName("a\u0000b"))
        assertNull(Validation.bio(""))
        assertNull(Validation.bio("строка\nвторая"))
        assertEquals(FieldErrorReason.TOO_LONG, Validation.bio("x".repeat(121)))
    }

    @Test
    fun `код - ровно шесть цифр`() {
        assertNull(Validation.code("012345"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.code("12345"))
        assertEquals(FieldErrorReason.INVALID_FORMAT, Validation.code("12345a"))
        assertEquals(FieldErrorReason.REQUIRED, Validation.code(""))
    }
}
