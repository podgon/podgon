package app.podgon.identity.security

import app.podgon.common.problem
import app.podgon.contract.ErrorCode
import app.podgon.identity.domain.TokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.json.JsonMapper

/** Stateless-защита API: access JWT в `Authorization: Bearer`, CSRF и cookie-сессий нет. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun jwtDecoder(tokens: TokenService): JwtDecoder = tokens.accessJwtDecoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        json: JsonMapper,
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
            }
        return http.build()
    }

    private fun problemEntryPoint(json: JsonMapper) =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _ ->
            response.setHeader("WWW-Authenticate", "Bearer")
            write(json, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED)
        }

    private fun problemAccessDenied(json: JsonMapper) =
        AccessDeniedHandler { _: HttpServletRequest, response: HttpServletResponse, _ ->
            write(json, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)
        }

    private fun write(
        json: JsonMapper,
        response: HttpServletResponse,
        status: HttpStatus,
        code: ErrorCode,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        json.writeValue(response.outputStream, problem(status, code))
    }
}
