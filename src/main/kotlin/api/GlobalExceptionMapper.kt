package api

import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response

open class GlobalExceptionMapper {
    @ServerExceptionMapper
    open fun mapException(x: EntityNotFoundException): RestResponse<Any?>? {
        return RestResponse.status<Any?>(Response.Status.NOT_FOUND, "Entity not found: ${x.message}")
    }
}