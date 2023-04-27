package testUtils

import common.ItemCategory
import common.UserRole
import infrastructure.entity.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
@Transactional
class EntityUtil {

    fun deleteAllData() {
        UserCommunityRelation.deleteAll()
        UserCommunityJoinRequest.deleteAll()
        ItemImage.deleteAll()
        ItemBooking.deleteAll()
        Item.deleteAll()
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
            10,
            20.0,
            -30.0,
            true
        )
        intercept(community)
        if (User.find("uuid", community.adminUuid).firstResult() == null) {
            setupUser { it.uuid = community.adminUuid }
        }
        community.persist()
        if (userUuid != null) {
            UserCommunityRelation(userUuid, community.uuid).persist()
        }
        return community
    }

    fun setupUserCommunityRelation(intercept: (UserCommunityRelation) -> Unit = {}): UserCommunityRelation {
        val userCommunityRelation = UserCommunityRelation(
            UUID.randomUUID(),
            UUID.randomUUID()
        )
        intercept(userCommunityRelation)
        userCommunityRelation.persist()
        return userCommunityRelation
    }

    fun setupUserCommunityJoinRequest(intercept: (UserCommunityJoinRequest) -> Unit = {}): UserCommunityJoinRequest {
        val userCommunityJoinRequest = UserCommunityJoinRequest(
            UUID.randomUUID(),
            UUID.randomUUID()
        )
        intercept(userCommunityJoinRequest)
        userCommunityJoinRequest.persist()
        return userCommunityJoinRequest
    }

    fun setupItem(intercept: (Item) -> Unit = {}): Item {
        val uuid = UUID.randomUUID()
        val item = Item(
            uuid,
            "name-$uuid",
            listOf(ItemCategory.OTHER),
            "street",
            "1",
            "36039",
            "Fulda",
            UUID.randomUUID(),
            UUID.randomUUID(),
            true,
            availability = null,
            availableUntil = null,
            description = "description-$uuid"
        )
        intercept(item)
        if (User.find("uuid", item.userUuid).firstResult() == null) {
            setupUser { it.uuid = item.userUuid }
        }
        if (Community.find("uuid", item.communityUuid).firstResult() == null) {
            setupCommunity { it.uuid = item.communityUuid }
        }
        item.persist()

        return item
    }

    fun setupItemImage(intercept: (ItemImage) -> Unit = {}): ItemImage {
        val itemImage = ItemImage(
            itemUuid = UUID.randomUUID(),
            path = "./item-images/${UUID.randomUUID()}.jpg"
        )
        intercept(itemImage)
        if (Item.find("uuid", itemImage.itemUuid).firstResult() == null) {
            setupItem { it.uuid = itemImage.itemUuid }
        }
        itemImage.persist()

        return itemImage
    }
}