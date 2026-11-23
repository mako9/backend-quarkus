package api.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The web and mobile clients send `requiresApproval` only when the owner has touched the switch, so the
 * default on this DTO is what a normal create request gets. Decided 2026-11-23: approval is required
 * unless the owner switches it off, so an omitted flag must mean `true`.
 */
class ItemRequestDtoTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `when an item request omits requiresApproval, then approval is required`() {
        val json = """
            {
              "name": "pressure washer",
              "categories": ["TOOL"],
              "communityUuid": "5c9f6f6a-2c6b-4b04-8b9a-1f0f0f0f0f0f",
              "isActive": true
            }
        """.trimIndent()

        val dto = mapper.readValue(json, ItemRequestDto::class.java)

        assertTrue(dto.requiresApproval)
    }
}
