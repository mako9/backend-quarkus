package api.controller

import common.JwtUserInfo
import domain.service.UserService
import io.quarkus.security.identity.SecurityIdentity
import jakarta.inject.Inject
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.*

abstract class Controller {
    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var jwt: JsonWebToken

    @Inject
    lateinit var keycloakSecurityContext: SecurityIdentity

    internal fun getUserUuid(): UUID {
        val jwtUserInfo = JwtUserInfo(jwt, keycloakSecurityContext)
        val userModel = userService.getUserByJwtUserInfo(jwtUserInfo)
        return userModel.uuid
    }
}