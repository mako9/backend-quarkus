package api.auth

import io.quarkus.security.identity.SecurityIdentity
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path

@Path("/api/users")
class UserResource {
    @Inject
    lateinit var keycloakSecurityContext: SecurityIdentity

    @GET
    @Path("/me")
    fun me(): User {
        return User(keycloakSecurityContext)
    }
}