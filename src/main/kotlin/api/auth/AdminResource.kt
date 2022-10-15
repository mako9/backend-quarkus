package api.auth

import javax.ws.rs.GET
import javax.ws.rs.Path

@Path("/admin")
class AdminResource {
    @GET
    fun manage(): String {
        return "granted"
    }
}