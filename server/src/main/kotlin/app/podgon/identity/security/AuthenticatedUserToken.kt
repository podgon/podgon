package app.podgon.identity.security

import app.podgon.identity.AuthenticatedUser
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/** Аутентификация запроса: принципал — [AuthenticatedUser], прав (authorities) нет: роли групп проверяются в сервисах. */
class AuthenticatedUserToken(
    private val user: AuthenticatedUser,
) : AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = ""

    override fun getPrincipal(): AuthenticatedUser = user
}

class JwtToAuthenticatedUser : Converter<Jwt, AuthenticatedUserToken> {
    override fun convert(jwt: Jwt): AuthenticatedUserToken =
        AuthenticatedUserToken(
            AuthenticatedUser(UUID.fromString(jwt.subject), UUID.fromString(jwt.getClaimAsString("sid"))),
        )
}
