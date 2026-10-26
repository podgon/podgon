package app.podgon.identity.security

import app.podgon.common.writeProblem
import app.podgon.contract.ErrorCode
import app.podgon.identity.domain.SessionService
import app.podgon.identity.domain.TokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** Stateless-защита API: access JWT в `Authorization: Bearer`, CSRF и cookie-сессий нет. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    /** Подпись и срок проверяет декодер токенов; затем — что сессия не отозвана и пользователь активен. */
    @Bean
    fun jwtDecoder(
        tokens: TokenService,
        sessions: SessionService,
    ): JwtDecoder {
        val signed = tokens.accessJwtDecoder()
        return JwtDecoder { token ->
            val jwt = signed.decode(token)
            val userId = runCatching { UUID.fromString(jwt.subject) }.getOrNull()
            val sessionId = runCatching { UUID.fromString(jwt.getClaimAsString("sid")) }.getOrNull()
            if (userId == null || sessionId == null || !sessions.isActive(userId, sessionId)) {
                throw BadJwtException("Сессия завершена")
            }
            jwt
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        json: JsonMapper,
        limiter: UserRateLimiter,
    ): SecurityFilterChain {
        val entryPoint = problemEntryPoint(json)
        val denied = problemAccessDenied(json)
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/openapi.yaml",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/account-deletion",
                        "/account-deletion.html",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer {
                it
                    .jwt { jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(JwtToAuthenticatedUser()) }
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(denied)
            }.exceptionHandling {
                it.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
            }.addFilterAfter(UserRateLimitFilter(limiter, json), BearerTokenAuthenticationFilter::class.java)
        return http.build()
    }

    private fun problemEntryPoint(json: JsonMapper) =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _ ->
            response.setHeader("WWW-Authenticate", "Bearer")
            response.writeProblem(json, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED)
        }

    private fun problemAccessDenied(json: JsonMapper) =
        AccessDeniedHandler { _: HttpServletRequest, response: HttpServletResponse, _ ->
            response.writeProblem(json, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)
        }
}
