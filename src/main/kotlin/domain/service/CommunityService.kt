package domain.service

import common.PageConfig
import domain.model.CommunityModel
import domain.model.PageModel
import domain.model.sort.CommunitySortBy
import infrastructure.entity.Community
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
            communityModel.radius
        )
        community.persist()
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
        community.persist()
        return CommunityModel(community)
    }

    fun getCommunityModel(uuid: UUID): CommunityModel? {
        val community = getCommunity(uuid) ?: return null
        return CommunityModel(community)
    }

    private fun getCommunity(uuid: UUID): Community? {
        return Community
            .find("#Community.getByUuid", uuid)
            .firstResult()
    }

    fun deleteCommunity(uuid: UUID) {
        Community.delete("#Community.deleteByUuid", uuid)
    }
}