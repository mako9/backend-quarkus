package api.controller

import api.dto.UserDto
import api.dto.UserPatchDto
import common.JwtUserInfo
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.apache.http.HttpStatus
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme

// register SecurityScheme once
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
@Path("/user")
@SecurityRequirement(name = "bearerAuth")
class UserController : Controller() {

    @GET
    @Path("/me")
    fun getOwnUser(): UserDto {
        val jwtUserInfo = JwtUserInfo(jwt, keycloakSecurityContext)
        return UserDto(userService.getUserByJwtUserInfo(jwtUserInfo))
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