package api.controller

import api.dto.CommunityDto
import api.dto.CommunityRequestDto
import api.dto.PageDto
import common.PageConfig
import domain.model.CommunityModel
import domain.model.sort.CommunitySortBy
import domain.service.CommunityService
import domain.service.UserService
import io.quarkus.panache.common.Sort
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.hibernate.validator.constraints.Range
import org.jboss.resteasy.reactive.RestPath
import java.util.*
import javax.inject.Inject
import javax.persistence.EntityNotFoundException
import javax.validation.constraints.Min
import javax.ws.rs.*
import javax.ws.rs.core.Response

@Path("/user/community")
class CommunityController {
    @Inject
    lateinit var communityService: CommunityService
    @Inject
    lateinit var userService: UserService
    @Inject
    lateinit var jwt: JsonWebToken

    @GET
    @Path("/")
    fun getCommunities(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
        @QueryParam("sortBy") sortBy: CommunitySortBy?,
        @QueryParam("sortDirection") sortDirection: Sort.Direction?,
    ): PageDto<CommunityDto> {
        val communityModelsPage = communityService.getCommunitiesPage(PageConfig(pageNumber, pageSize), sortBy, sortDirection)
        return PageDto.of(communityModelsPage, ::CommunityDto)
    }

    @GET
    @Path("/my")
    fun getOwnCommunities(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
    ): PageDto<CommunityDto> {
        val userUuid = getUserUuid()

        val communityModelsPage = communityService.getCommunitiesPageByUser(PageConfig(pageNumber, pageSize), userUuid)
        return PageDto.of(communityModelsPage, ::CommunityDto)
    }

    @POST
    @Path("/")
    fun createCommunity(
        communityRequestDto: CommunityRequestDto
    ): CommunityDto {
        val userUuid = getUserUuid()

        var communityModel = CommunityModel(
            UUID.randomUUID(),
            communityRequestDto.name,
            communityRequestDto.street,
            communityRequestDto.houseNumber,
            communityRequestDto.postalCode,
            communityRequestDto.city,
            userUuid,
            communityRequestDto.radius
        )
        communityModel = communityService.insertCommunity(communityModel)
        return CommunityDto(communityModel)
    }

    @PATCH
    @Path("/{uuid}")
    fun updateCommunity(
        @RestPath uuid: UUID,
        communityRequestDto: CommunityRequestDto
    ): CommunityDto {
        var communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")

        checkUserAdminRight(communityModel)

        communityModel.name = communityRequestDto.name
        communityModel.street = communityRequestDto.street
        communityModel.houseNumber = communityRequestDto.houseNumber
        communityModel.postalCode = communityRequestDto.postalCode
        communityModel.city = communityRequestDto.city
        communityModel.radius = communityRequestDto.radius

        communityModel = communityService.updateCommunity(communityModel)

        return CommunityDto(communityModel)
    }

    @DELETE
    @Path("/{uuid}")
    fun deleteCommunity(
        @RestPath uuid: UUID
    ): Response {
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        checkUserAdminRight(communityModel)
        communityService.deleteCommunity(uuid)

        return Response.noContent().build()
    }

    private fun checkUserAdminRight(communityModel: CommunityModel) {
        val userUuid = getUserUuid()
        if (communityModel.adminUuid != userUuid) throw ForbiddenException("Not an admin user of this community")
    }

    private fun getUserUuid(): UUID {
        val mail = jwt.getClaim<String>(Claims.email)
        val userModel = userService.getUserByMail(mail) ?: throw NotFoundException("No user for mail $mail")
        return userModel.uuid
    }
}