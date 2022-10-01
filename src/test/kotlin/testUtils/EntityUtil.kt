package testUtils

import common.UserRole
import infrastructure.entity.User
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class EntityUtil {

    fun deleteAllData() {
        User.deleteAll()
    }

    fun setupUser(intercept: (User) -> Unit = {}): User {
        val user = User(
            UUID.randomUUID(),
            "alice",
            "alice",
            "alice@test.tld",
            "street",
            "1",
            "36039",
            "Fulda",
            listOf(UserRole.USER)
        )
        intercept(user)
        user.persist()
        return user
    }
}