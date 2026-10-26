package app.podgon.identity.domain

import app.podgon.identity.AuthProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256 одноразовых кодов с серверным секретом: в БД код в открытом виде не хранится. */
@Component
class CodeHasher(
    props: AuthProperties,
) {
    private val key = SecretKeySpec(props.codeHmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    /** В подпись входит email, поэтому код нельзя перенести на другой адрес. */
    fun hash(
        email: String,
        code: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal("$email:$code".toByteArray(Charsets.UTF_8))
    }

    /** Сравнение за постоянное время. */
    fun matches(
        expected: ByteArray,
        email: String,
        code: String,
    ): Boolean = MessageDigest.isEqual(expected, hash(email, code))
}
