package api.auth

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement

@Path("/admin")
@SecurityRequirement(name = "bearerAuth")
class AdminResource {
    @GET
    fun manage(): String {
        return "granted"
    }
}