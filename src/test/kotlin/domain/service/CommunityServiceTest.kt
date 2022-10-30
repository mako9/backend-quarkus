package domain.service

import common.PageConfig
import domain.model.CommunityModel
import domain.model.sort.CommunitySortBy
import infrastructure.entity.Community
import infrastructure.entity.User
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import testUtils.EntityUtil
import java.util.*
import javax.inject.Inject

@QuarkusTest
class CommunityServiceTest {
    @Inject
    private lateinit var entityUtil: EntityUtil
    @Inject
    private lateinit var communityService: CommunityService

    @Nested
    @DisplayName("given communities exist")
    inner class GivenCommunitiesExist {
        private lateinit var communityOne: Community
        private lateinit var communityTwo: Community

        @BeforeEach
        fun beforeEach() {
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
            val communityPage = communityService.getCommunitiesPage(pageConfig, null, null)
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
            val communityPage = communityService.getCommunitiesPage(pageConfig, CommunitySortBy.CITY, Sort.Direction.Descending)
            assertEquals(CommunityModel(communityTwo), communityPage.content.first())
            assertEquals(CommunityModel(communityOne), communityPage.content.last())
        }

        @Test
        fun `when changing page config of paginated communities, then correct page of community models is returned`() {
            val pageConfig = PageConfig(1, 1)
            val communityPage = communityService.getCommunitiesPage(pageConfig, null, null)
            assertEquals(2, communityPage.totalPages)
            assertEquals(false, communityPage.isFirstPage)
            assertEquals(true, communityPage.isLastPage)
            assertEquals(pageConfig.pageSize, communityPage.pageSize)
            assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
            assertEquals(1, communityPage.content.size)
            assertEquals(CommunityModel(communityTwo), communityPage.content.first())
        }
    }

    @Nested
    @DisplayName("given specific user exists")
    inner class GivenSpecificUserExist {
        private lateinit var user: User

        @BeforeEach
        fun beforeEach() {
            user = entityUtil.setupUser()
        }

        @AfterEach
        fun afterEach() {
            entityUtil.deleteAllData()
        }

        @Test
        fun `when retrieving paginated communities for specific user, then correct page of community models is returned`() {
            val communityOne = entityUtil.setupCommunity(user.uuid) { it.name = "one"; it.city = "one" }
            entityUtil.setupCommunity { it.name = "two"; it.city = "two" }
            val communityPage = communityService.getCommunitiesPageByUser(PageConfig(), user.uuid)
            assertEquals(1, communityPage.content.size)
            assertEquals(CommunityModel(communityOne), communityPage.content.first())
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
                30.0
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
                30.0
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
    }
}