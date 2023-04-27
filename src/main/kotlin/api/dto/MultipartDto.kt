package api.dto

import jakarta.ws.rs.FormParam
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.jboss.resteasy.reactive.multipart.FileUpload

class MultipartDto {
    @FormParam("file")
    @Schema(type = SchemaType.STRING, format = "binary")
    lateinit var file: FileUpload
}