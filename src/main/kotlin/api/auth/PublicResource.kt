package api.auth

import javax.ws.rs.GET
import javax.ws.rs.Path

@Path("/api/public")
class PublicResource {
    @GET
    fun serve() {
        // no-op
    }
}