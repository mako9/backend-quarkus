package testUtils

import common.UserRole
import infrastructure.entity.Community
import infrastructure.entity.User
import infrastructure.entity.UserCommunityRelation
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class EntityUtil {

    fun deleteAllData() {
        UserCommunityRelation.deleteAll()
        Community.deleteAll()
        User.deleteAll()
    }

    fun setupUser(intercept: (User) -> Unit = {}): User {
        val uuid = UUID.randomUUID()
        val user = User(
            uuid,
            "alice",
            "alice",
            "$uuid@test.tld",
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

    fun setupCommunity(userUuid: UUID? = null, intercept: (Community) -> Unit = {}): Community {
        val uuid = UUID.randomUUID()
        val community = Community(
            uuid,
            "test-$uuid",
            "street",
            "1",
            "36039",
            "Fulda",
            UUID.randomUUID(),
            10
        )
        intercept(community)
        if (User.find("#User.findByUuid", community.adminUuid).firstResult() == null) {
            setupUser { it.uuid = community.adminUuid }
        }
        community.persist()
        if (userUuid != null) {
            UserCommunityRelation(userUuid, community.uuid).persist()
        }
        return community
    }
}