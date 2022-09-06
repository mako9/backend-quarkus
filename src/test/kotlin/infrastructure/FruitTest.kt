package infrastructure

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
class FruitTest {
    @Test
    fun `find by id, should return correct fruit`() {
        assertEquals("Apple", Fruit.findById(2)?.name)
    }
}
