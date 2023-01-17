package api.dto

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.jboss.resteasy.reactive.multipart.FileUpload
import javax.ws.rs.FormParam

class MultipartDto {
    @FormParam("file")
    @Schema(type = SchemaType.STRING, format = "binary")
    lateinit var file: FileUpload
}