package infrastructure

import org.junit.jupiter.api.Test
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals

@QuarkusTest
class FruitTest {
    @Test
    fun `find by id, should return correct fruit`() {
        assertEquals("Apple", Fruit.findById(2)?.name)
    }
}
