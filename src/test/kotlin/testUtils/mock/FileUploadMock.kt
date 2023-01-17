package testUtils.mock

import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class FileUploadMock : FileUpload {
    private var path: String

    init {
        val tempFile = File.createTempFile("test", ".jpg")
        tempFile.deleteOnExit()
        path = tempFile.path
    }
    override fun name(): String {
        return "test"
    }

    override fun filePath(): Path {
        return Paths.get(path)
    }

    override fun fileName(): String {
        return "test.jpg"
    }

    override fun size(): Long {
        return 20
    }

    override fun contentType(): String {
        return "jpg"
    }

    override fun charSet(): String {
        return "utf-8"
    }
}