package api.dto

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.jboss.resteasy.reactive.multipart.FileUpload
import javax.ws.rs.FormParam

// Interface that will define the OpenAPI schema for the binary type input (upload)
@Schema(type = SchemaType.STRING, format = "binary")
interface UploadItemSchema

// Class that will be used to define the request body, and with that
// it will allow uploading of "N" files
class UploadFormSchema {
    val files: List<UploadItemSchema> = emptyList()
}

// We instruct OpenAPI to use the schema provided by the 'UploadFormSchema'
// class implementation and thus define a valid OpenAPI schema for the Swagger
// UI
@Schema(implementation = UploadFormSchema::class)
class MultipartDto {
    @FormParam("files")
    lateinit var files: List<FileUpload>
}