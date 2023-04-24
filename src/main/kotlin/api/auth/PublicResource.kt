package api.auth

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

@Path("/public")
class PublicResource {
    @GET
    fun serve() {
        // no-op
    }
}