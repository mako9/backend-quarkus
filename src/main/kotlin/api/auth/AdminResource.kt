package api.auth

import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import javax.ws.rs.GET
import javax.ws.rs.Path

@Path("/admin")
@SecurityRequirement(name = "bearerAuth")
class AdminResource {
    @GET
    fun manage(): String {
        return "granted"
    }
}