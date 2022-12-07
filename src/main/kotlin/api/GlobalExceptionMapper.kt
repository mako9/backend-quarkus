package api

import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomForbiddenException
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response

open class GlobalExceptionMapper {
    @ServerExceptionMapper
    open fun mapNotFoundException(x: EntityNotFoundException): RestResponse<Any?>? {
        return RestResponse.status<Any?>(Response.Status.NOT_FOUND, "Entity not found: ${x.message}")
    }

    @ServerExceptionMapper
    open fun mapBadRequestException(x: CustomBadRequestException): RestResponse<Any?>? {
        return RestResponse.status<Any?>(Response.Status.BAD_REQUEST, "Bad request: ${x.message}")
    }

    @ServerExceptionMapper
    open fun mapForbiddenException(x: CustomForbiddenException): RestResponse<Any?>? {
        return RestResponse.status<Any?>(Response.Status.FORBIDDEN, "Forbidden: ${x.message}")
    }
}