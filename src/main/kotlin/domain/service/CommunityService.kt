package domain.service

import common.PageConfig
import domain.model.CommunityModel
import domain.model.PageModel
import domain.model.exception.CustomBadRequestException
import domain.model.sort.CommunitySortBy
import infrastructure.entity.Community
import infrastructure.entity.UserCommunityJoinRequest
import infrastructure.entity.UserCommunityRelation
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class CommunityService {

    fun getCommunitiesPage(userUuid: UUID, pageConfig: PageConfig, sortBy: CommunitySortBy?, sortDirection: Sort.Direction?): PageModel<CommunityModel> {
        val sortByValue = sortBy?.name ?: CommunitySortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Community
            .find(query = Community.queryAllByNotUserUuid, sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::CommunityModel)
    }

    fun getCommunitiesPageByUser(userUuid: UUID, pageConfig: PageConfig, sortBy: CommunitySortBy?, sortDirection: Sort.Direction?): PageModel<CommunityModel> {
        val sortByValue = sortBy?.name ?: CommunitySortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Community
            .find(query = Community.queryAllByUserUuid, sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::CommunityModel)
    }

    fun getCommunitiesPageByAdmin(adminUuid: UUID, pageConfig: PageConfig, sortBy: CommunitySortBy?, sortDirection: Sort.Direction?): PageModel<CommunityModel> {
        val sortByValue = sortBy?.name ?: CommunitySortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Community
            .find(query = "adminUuid", Sort.by(sortByValue, sortDirectionValue), adminUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::CommunityModel)
    }

    fun insertCommunity(communityModel: CommunityModel): CommunityModel  {
        val community = communityModel.toCommunity()
        community.persist()
        UserCommunityRelation(
            community.adminUuid,
            communityModel.uuid
        ).persist()
        return CommunityModel(community)
    }

    fun updateCommunity(communityModel: CommunityModel): CommunityModel  {
        val community = getCommunity(communityModel.uuid) ?: throw EntityNotFoundException("No community for UUID: ${communityModel.uuid}")
        community.name = communityModel.name
        community.street = communityModel.street
        community.houseNumber = communityModel.houseNumber
        community.postalCode = communityModel.postalCode
        community.city = communityModel.city
        community.radius = communityModel.radius
        community.latitude = communityModel.latitude
        community.longitude = communityModel.longitude
        community.persist()
        return CommunityModel(community)
    }

    fun getCommunityModel(uuid: UUID): CommunityModel? {
        val community = getCommunity(uuid) ?: return null
        return CommunityModel(community)
    }

    private fun getCommunity(uuid: UUID): Community? {
        return Community
            .find("uuid", uuid)
            .firstResult()
    }

    fun deleteCommunity(uuid: UUID) {
        UserCommunityRelation.delete("communityUuid", uuid)
        Community.delete("uuid", uuid)
    }

    fun joinCommunity(userUuid: UUID, communityUuid: UUID) {
        val community = getCommunity(communityUuid) ?: throw EntityNotFoundException("No community for UUID: $communityUuid")
        if (!community.canBeJoined) throw CustomBadRequestException(message = "Community can not be joined.")
        UserCommunityJoinRequest(
            userUuid,
            communityUuid
        ).persist()
    }

    fun leaveCommunity(userUuid: UUID, communityUuid: UUID) {
        getCommunity(communityUuid) ?: throw EntityNotFoundException("No community for UUID: $communityUuid")
        val relation = UserCommunityRelation.find("userUuid = ?1 AND communityUuid = ?2", userUuid, communityUuid).firstResult()
            ?: throw CustomBadRequestException(message = "Not a member of community: $communityUuid")
        relation.delete()
    }

    fun approveRequestsForUsers(communityUuid: UUID, userUuids: List<UUID>) {
        val requests = getRequests(communityUuid, userUuids)
        val userCommunityRelations = requests.map { UserCommunityRelation(it.userUuid, it.communityUuid) }
        UserCommunityRelation.persist(userCommunityRelations)
        UserCommunityJoinRequest
            .delete("communityUuid = ?1 AND userUuid IN ?2", communityUuid, userUuids)
    }

    fun declineRequestsForUsers(communityUuid: UUID, userUuids: List<UUID>) {
        getRequests(communityUuid, userUuids)
        UserCommunityJoinRequest
            .delete("communityUuid = ?1 AND userUuid IN ?2", communityUuid, userUuids)
    }

    fun isUserCommunityMember(communityUuid: UUID, userUuid: UUID): Boolean {
        return UserCommunityRelation.find("communityUuid = ?1 AND userUuid = ?2", communityUuid, userUuid).firstResult() != null
    }

    fun hasUserRequestedMembership(communityUuid: UUID, userUuid: UUID): Boolean {
        return UserCommunityJoinRequest.find("communityUuid = ?1 AND userUuid = ?2", communityUuid, userUuid).firstResult() != null
    }

    private fun getRequests(communityUuid: UUID, userUuids: List<UUID>): List<UserCommunityJoinRequest>  {
        val requests = UserCommunityJoinRequest
            .find("communityUuid = ?1 AND userUuid IN ?2", communityUuid, userUuids)
            .list()
        val userUuidsWithMissingRequests = userUuids.filter { userUuid -> !requests.map { it.userUuid }.contains(userUuid) }
        if (userUuidsWithMissingRequests.isNotEmpty()) throw CustomBadRequestException(message = "No requests found for users: $userUuidsWithMissingRequests")
        return requests
    }
}