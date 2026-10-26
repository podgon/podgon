package app.podgon.identity.domain

import app.podgon.identity.AuthProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * Выпуск и проверка JWT (HS256, ключи с `kid` для ротации). Access-токен и регистрационный токен
 * различаются claim `token_use`, поэтому один нельзя предъявить вместо другого.
 */
@Service
class TokenService(
    private val props: AuthProperties,
    private val clock: Clock,
) {
    private val encoder: NimbusJwtEncoder
    private val accessDecoder: NimbusJwtDecoder
    private val registrationDecoder: NimbusJwtDecoder

    init {
        require(props.jwtKeys.isNotEmpty()) { "podgon.auth.jwt-keys не задан" }
        require(props.jwtKeys.containsKey(props.jwtActiveKid)) { "podgon.auth.jwt-active-kid не найден среди jwt-keys" }
        val jwks =
            props.jwtKeys.map { (kid, secret) ->
                val bytes = Base64.getDecoder().decode(secret)
                require(bytes.size >= 32) { "Ключ JWT '$kid' короче 32 байт" }
                OctetSequenceKey
                    .Builder(bytes)
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.HS256)
                    .build()
            }
        val source = ImmutableJWKSet<SecurityContext>(JWKSet(jwks))
        encoder = NimbusJwtEncoder(source)
        accessDecoder = decoder(source, ACCESS)
        registrationDecoder = decoder(source, REGISTRATION)
    }

    /** Декодер access-токенов для Spring Security (resource server). */
    fun accessJwtDecoder(): JwtDecoder = accessDecoder

    fun issueAccessToken(
        userId: UUID,
        sessionId: UUID,
    ): String = issue(ACCESS, userId.toString(), props.accessTtl) { it.claim("sid", sessionId.toString()) }

    /** Подтверждённый кодом email, по которому можно завершить регистрацию. */
    fun issueRegistrationToken(email: String): String = issue(REGISTRATION, email, props.registrationTtl) { it }

    /** Email из регистрационного токена либо `null`, если токен неверен или просрочен. */
    fun parseRegistrationToken(token: String): String? =
        try {
            registrationDecoder.decode(token).subject
        } catch (e: JwtException) {
            null
        }

    private fun issue(
        use: String,
        subject: String,
        ttl: Duration,
        customize: (JwtClaimsSet.Builder) -> JwtClaimsSet.Builder,
    ): String {
        val now = clock.instant()
        val claims =
            customize(
                JwtClaimsSet
                    .builder()
                    .issuer(props.issuer)
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plus(ttl))
                    .id(UUID.randomUUID().toString())
                    .claim(TOKEN_USE, use),
            ).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).keyId(props.jwtActiveKid).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun decoder(
        source: ImmutableJWKSet<SecurityContext>,
        use: String,
    ): NimbusJwtDecoder {
        val processor = DefaultJWTProcessor<SecurityContext>()
        processor.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.HS256, source)
        // Сроки проверяет JwtTimestampValidator с нашими часами
        processor.jwtClaimsSetVerifier =
            com.nimbusds.jwt.proc
                .JWTClaimsSetVerifier { _, _ -> }
        val decoder = NimbusJwtDecoder(processor)
        val timestamps = JwtTimestampValidator(Duration.ofSeconds(5))
        timestamps.setClock(clock)
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(timestamps, JwtIssuerValidator(props.issuer), tokenUseValidator(use)),
        )
        return decoder
    }

    private fun tokenUseValidator(use: String) =
        OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.getClaimAsString(TOKEN_USE) == use) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Неверный тип токена", null))
            }
        }

    private companion object {
        const val TOKEN_USE = "token_use"
        const val ACCESS = "access"
        const val REGISTRATION = "registration"
    }
}
