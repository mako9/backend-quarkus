package api

import api.dto.ErrorDto
import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomException
import domain.model.exception.CustomForbiddenException
import io.quarkus.logging.Log
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper
import javax.persistence.EntityNotFoundException
import javax.ws.rs.core.Response

open class GlobalExceptionMapper {
    @ServerExceptionMapper
    open fun mapNotFoundException(x: EntityNotFoundException): RestResponse<Any?>? {
        Log.warn("Could not find entity: $x")
        return RestResponse.status<Any?>(Response.Status.NOT_FOUND, "Entity not found: ${x.message}")
    }

    @ServerExceptionMapper
    open fun mapCustomException(x: CustomException): RestResponse<Any?>? {
        val status = when (x) {
            is CustomBadRequestException -> Response.Status.BAD_REQUEST
            is CustomForbiddenException -> Response.Status.FORBIDDEN
        }
        Log.warn("Received custom exception with status $status: $x")
        return RestResponse.status<Any?>(status, ErrorDto(exception = x))
    }
}