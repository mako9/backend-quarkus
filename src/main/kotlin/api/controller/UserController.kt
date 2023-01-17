package api.controller

import api.dto.UserDto
import api.dto.UserPatchDto
import common.UserRole
import domain.service.UserService
import io.quarkus.security.identity.SecurityIdentity
import org.apache.http.HttpStatus
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.PATCH
import javax.ws.rs.Path
import javax.ws.rs.core.Response

// register SecurityScheme once
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
@Path("/user")
@SecurityRequirement(name = "bearerAuth")
class UserController {
    @Inject
    lateinit var userService: UserService
    @Inject
    lateinit var keycloakSecurityContext: SecurityIdentity
    @Inject
    lateinit var jwt: JsonWebToken

    @GET
    @Path("/me")
    fun getOwnUser(): UserDto {
        val mail = jwt.getClaim<String>(Claims.email)
        val userModel = userService.getUserByMail(mail)
        if (userModel != null) return UserDto(userModel)
        val createdUserModel = userService.createUser(
            mail,
            jwt.getClaim(Claims.given_name),
            jwt.getClaim(Claims.family_name),
            keycloakSecurityContext.roles.mapNotNull { UserRole.valueOf(it.uppercase()) }
        )
        return UserDto(createdUserModel)
    }

    @PATCH
    @Path("/me")
    fun updateOwnUser(userPatchDto: UserPatchDto): Response {
        val mail = jwt.getClaim<String>(Claims.email)
        val userModel = userService.updateUser(
            mail,
            firstName = userPatchDto.firstName,
            lastName = userPatchDto.lastName,
            street = userPatchDto.street,
            houseNumber = userPatchDto.houseNumber,
            postalCode = userPatchDto.postalCode,
            city = userPatchDto.city
        )
        return Response.ok(UserDto(userModel)).status(HttpStatus.SC_OK).build()
    }
}