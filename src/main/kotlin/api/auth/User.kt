package api.auth

import io.quarkus.security.identity.SecurityIdentity

data class User(
    val name: String,
    val role: List<UserRole>
) {
    constructor(securityContext: SecurityIdentity): this(
        securityContext.principal.name,
        securityContext.roles.map { UserRole.valueOf(it.uppercase()) }
    )
}

enum class UserRole {
    USER,
    ADMIN,
    CONFIDENTIAL
}