package domain.service

import common.PageConfig
import domain.model.CommunityModel
import domain.model.exception.CustomBadRequestException
import domain.model.sort.CommunitySortBy
import infrastructure.entity.Community
import infrastructure.entity.User
import infrastructure.entity.UserCommunityJoinRequest
import infrastructure.entity.UserCommunityRelation
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import testUtils.EntityUtil
import java.util.*

@QuarkusTest
class CommunityServiceTest {
    @Inject
    private lateinit var entityUtil: EntityUtil

    @Inject
    private lateinit var communityService: CommunityService

    private lateinit var user: User
    private lateinit var communityOne: Community
    private lateinit var communityTwo: Community

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser()
        communityOne = entityUtil.setupCommunity { it.name = "one"; it.city = "one" }
        communityTwo = entityUtil.setupCommunity { it.name = "two"; it.city = "two" }
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
    }

    @Test
    fun `when retrieving paginated communities, then correct page of community models is returned`() {
        val pageConfig = PageConfig()
        val communityPage = communityService.getCommunitiesPage(user.uuid, pageConfig, null, null)
        assertEquals(1, communityPage.totalPages)
        assertEquals(true, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(2, communityPage.content.size)
        assertEquals(CommunityModel(communityOne), communityPage.content.first())
        assertEquals(CommunityModel(communityTwo), communityPage.content.last())
    }

    @Test
    fun `when sorting paginated communities, then correct page order of community models is returned`() {
        val pageConfig = PageConfig()
        val communityPage =
            communityService.getCommunitiesPage(user.uuid, pageConfig, CommunitySortBy.CITY, Sort.Direction.Descending)
        assertEquals(CommunityModel(communityTwo), communityPage.content.first())
        assertEquals(CommunityModel(communityOne), communityPage.content.last())
    }

    @Test
    fun `when changing page config of paginated communities, then correct page of community models is returned`() {
        val pageConfig = PageConfig(1, 1)
        val communityPage = communityService.getCommunitiesPage(user.uuid, pageConfig, null, null)
        assertEquals(2, communityPage.totalPages)
        assertEquals(false, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(1, communityPage.content.size)
        assertEquals(CommunityModel(communityTwo), communityPage.content.first())
    }

    @Test
    fun `when retrieving paginated communities for specific user, then correct page of community models is returned`() {
        val communityThree = entityUtil.setupCommunity(user.uuid) { it.name = "three"; it.city = "three" }
        val communityPage = communityService.getCommunitiesPageByUser(user.uuid, PageConfig(), null, null)
        assertEquals(1, communityPage.content.size)
        assertEquals(CommunityModel(communityThree), communityPage.content.first())
    }

    @Test
    fun `when insert community, then correct community entity is stored and returned`() {
        val communityModel = CommunityModel(
            UUID.randomUUID(),
            "test",
            null,
            null,
            null,
            null,
            user.uuid,
            10,
            20.0,
            30.0,
            true
        )
        val result = communityService.insertCommunity(communityModel)
        assertEquals(communityModel, result)
        assertNotNull(Community.find("uuid", communityModel.uuid).firstResult())
    }

    @Test
    fun `when updating community, then correct community entity is updated and returned`() {
        val community = entityUtil.setupCommunity { it.adminUuid = user.uuid }
        val communityModel = CommunityModel(
            community.uuid,
            "test - update",
            "street - update",
            null,
            null,
            null,
            user.uuid,
            11,
            20.0,
            30.0,
            true
        )
        val result = communityService.updateCommunity(communityModel)
        val updatedCommunity = Community.find("uuid", communityModel.uuid).firstResult()
        assertEquals(communityModel, result)
        assertEquals(communityModel.name, updatedCommunity?.name)
        assertEquals(communityModel.street, updatedCommunity?.street)
        assertEquals(communityModel.radius, updatedCommunity?.radius)
    }

    @Test
    fun `when getting specific community, then correct community is returned`() {
        val community = entityUtil.setupCommunity()
        val result = communityService.getCommunityModel(community.uuid)
        assertEquals(CommunityModel(community), result)
    }

    @Test
    fun `when getting non-existing specific community, then null is returned`() {
        val result = communityService.getCommunityModel(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `when deleting specific community, then correct community is deleted`() {
        val community = entityUtil.setupCommunity()
        communityService.deleteCommunity(community.uuid)
        assertNull(Community.find("uuid", community.uuid).firstResult())
    }

    @Test
    fun `when joining specific community, then correct UserCommunityJoinRequest entity exists`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        communityService.joinCommunity(user.uuid, community.uuid)
        val request = UserCommunityJoinRequest.find("userUuid", user.uuid).firstResult()
        assertEquals(user.uuid, request?.userUuid)
        assertEquals(community.uuid, request?.communityUuid)
    }

    @Test
    fun `when joining non-existing community, then correct Exception is thrown`() {
        val user = entityUtil.setupUser()
        val unknownUuid = UUID.randomUUID()
        assertThrows<EntityNotFoundException>("No community for UUID: $unknownUuid") {
            communityService.joinCommunity(user.uuid, unknownUuid)
        }
    }

    @Test
    fun `when joining non-joinable community, then correct Exception is thrown`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity { it.canBeJoined = false }
        assertThrows<CustomBadRequestException>("Forbidden: Community can not be joined.") {
            communityService.joinCommunity(user.uuid, community.uuid)
        }
    }

    @Test
    fun `when leaving specific community, then correct UserCommunityRelation is deleted`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        entityUtil.setupUserCommunityRelation { it.userUuid = user.uuid; it.communityUuid = community.uuid }
        communityService.leaveCommunity(user.uuid, community.uuid)
        assertNull(UserCommunityRelation.find("userUuid", user.uuid).firstResult())
    }

    @Test
    fun `when leaving non-existing community, then correct error is thrown`() {
        val user = entityUtil.setupUser()
        val unknownUuid = UUID.randomUUID()
        assertThrows<EntityNotFoundException>("No community for UUID: $unknownUuid") {
            communityService.leaveCommunity(user.uuid, unknownUuid)
        }
    }

    @Test
    fun `when leaving non-joined community, then correct error is thrown`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        assertThrows<CustomBadRequestException>("Not a member of community: ${community.uuid}") {
            communityService.leaveCommunity(user.uuid, community.uuid)
        }
    }

    @Test
    fun `when approving a join request, then correct entities exist`() {
        val userOne = entityUtil.setupUser()
        val userTwo = entityUtil.setupUser { it.mail = "test2@test.tld" }
        val community = entityUtil.setupCommunity()
        entityUtil.setupUserCommunityJoinRequest { it.communityUuid = community.uuid; it.userUuid = userOne.uuid }
        entityUtil.setupUserCommunityJoinRequest { it.communityUuid = community.uuid; it.userUuid = userTwo.uuid }
        communityService.approveRequestsForUsers(community.uuid, listOf(userOne.uuid, userTwo.uuid))
        assertNotNull(
            UserCommunityRelation.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userOne.uuid)
                .firstResult()
        )
        assertNotNull(
            UserCommunityRelation.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userTwo.uuid)
                .firstResult()
        )
        assertNull(
            UserCommunityJoinRequest.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userOne.uuid)
                .firstResult()
        )
        assertNull(
            UserCommunityJoinRequest.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userTwo.uuid)
                .firstResult()
        )
    }

    @Test
    fun `when approving non-existing request, then correct Exception is thrown`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        assertThrows<CustomBadRequestException>("No requests found for users: ${listOf(user.uuid)}") {
            communityService.approveRequestsForUsers(community.uuid, listOf(user.uuid))
        }
    }

    @Test
    fun `when declining a join request, then requests are deleted`() {
        val userOne = entityUtil.setupUser()
        val userTwo = entityUtil.setupUser { it.mail = "test2@test.tld" }
        val community = entityUtil.setupCommunity()
        entityUtil.setupUserCommunityJoinRequest { it.communityUuid = community.uuid; it.userUuid = userOne.uuid }
        entityUtil.setupUserCommunityJoinRequest { it.communityUuid = community.uuid; it.userUuid = userTwo.uuid }
        communityService.declineRequestsForUsers(community.uuid, listOf(userOne.uuid, userTwo.uuid))
        assertNull(
            UserCommunityJoinRequest.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userOne.uuid)
                .firstResult()
        )
        assertNull(
            UserCommunityJoinRequest.find("communityUuid = ?1 AND userUuid = ?2", community.uuid, userTwo.uuid)
                .firstResult()
        )
    }

    @Test
    fun `when declining non-existing request, then correct exception is thrown`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        assertThrows<CustomBadRequestException>("No requests found for users: ${listOf(user.uuid)}") {
            communityService.declineRequestsForUsers(community.uuid, listOf(user.uuid))
        }
    }

    @Test
    fun `when requesting join relation exists, then hasUserRequestedMembership returns true`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()
        entityUtil.setupUserCommunityJoinRequest { it.communityUuid = community.uuid; it.userUuid = user.uuid }

        assertTrue(communityService.hasUserRequestedMembership(community.uuid, user.uuid))
    }

    @Test
    fun `when requesting join relation does not exist, then hasUserRequestedMembership returns true`() {
        val user = entityUtil.setupUser()
        val community = entityUtil.setupCommunity()

        assertFalse(communityService.hasUserRequestedMembership(community.uuid, user.uuid))
    }
}