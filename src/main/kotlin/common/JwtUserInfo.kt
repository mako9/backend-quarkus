package common

import io.quarkus.security.identity.SecurityIdentity
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken

data class JwtUserInfo(
    val mail: String,
    val firstName: String,
    val lastName: String,
    val roles: List<UserRole>
) {
    constructor(jwt: JsonWebToken, keycloakSecurityContext: SecurityIdentity) : this(
        mail = jwt.getClaim<String>(Claims.email),
        firstName = jwt.getClaim(Claims.given_name),
        lastName = jwt.getClaim(Claims.family_name),
        roles = keycloakSecurityContext.roles.mapNotNull { UserRole.valueOf(it.uppercase()) }
    )
}