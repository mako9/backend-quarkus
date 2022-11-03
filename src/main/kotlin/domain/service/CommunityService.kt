package domain.service

import common.PageConfig
import domain.model.CommunityModel
import domain.model.PageModel
import domain.model.exception.CustomBadRequestException
import domain.model.sort.CommunitySortBy
import infrastructure.entity.Community
import infrastructure.entity.UserCommunityRelation
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Parameters
import io.quarkus.panache.common.Sort
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class CommunityService {

    fun getCommunitiesPage(pageConfig: PageConfig, sortBy: CommunitySortBy?, sortDirection: Sort.Direction?): PageModel<CommunityModel> {
        val sortByValue = sortBy?.name ?: CommunitySortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Community
            .findAll(Sort.by(sortByValue, sortDirectionValue))
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::CommunityModel)
    }

    fun getCommunitiesPageByUser(pageConfig: PageConfig, userUuid: UUID): PageModel<CommunityModel> {
        val query = Community
            .find(query = "#Community.getByUserUuid",
                Parameters.with("userUuid", userUuid))
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::CommunityModel)
    }

    fun insertCommunity(communityModel: CommunityModel): CommunityModel  {
        val community = Community(
            communityModel.uuid,
            communityModel.name,
            communityModel.street,
            communityModel.houseNumber,
            communityModel.postalCode,
            communityModel.city,
            communityModel.adminUuid,
            communityModel.radius,
            communityModel.latitude,
            communityModel.longitude,
            communityModel.canBeJoined
        )
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
        Community.delete("uuid", uuid)
    }

    fun joinCommunity(userUuid: UUID, communityUuid: UUID) {
        val community = getCommunity(communityUuid) ?: throw EntityNotFoundException("No community for UUID: $communityUuid")
        if (!community.canBeJoined) throw CustomBadRequestException("Community can not be joined.")
        UserCommunityRelation(
            userUuid,
            communityUuid
        ).persist()
    }
}