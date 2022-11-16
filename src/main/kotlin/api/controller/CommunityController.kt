package api.controller

import api.dto.*
import common.PageConfig
import domain.model.CommunityModel
import domain.model.sort.CommunitySortBy
import domain.service.CommunityService
import domain.service.UserService
import io.quarkus.panache.common.Sort
import org.apache.http.HttpStatus
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.hibernate.validator.constraints.Range
import org.jboss.resteasy.reactive.ResponseStatus
import org.jboss.resteasy.reactive.RestPath
import java.util.*
import javax.inject.Inject
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
    fun getMyCommunities(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
    ): PageDto<CommunityDto> {
        val userUuid = getUserUuid()

        val communityModelsPage = communityService.getCommunitiesPageByUser(PageConfig(pageNumber, pageSize), userUuid)
        return PageDto.of(communityModelsPage, ::CommunityDto)
    }

    @GET
    @Path("/{uuid}")
    fun getCommunity(
        @RestPath uuid: UUID
    ): CommunityDetailDto {
        val userUuid = getUserUuid()
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        val admin = userService.getUserByUuid(communityModel.adminUuid)

        val dto = CommunityDetailDto.createWithIsAdmin(communityModel, userUuid)
        dto.adminFirstName = admin?.firstName
        dto.adminLastName = admin?.lastName
        dto.isMember = communityService.isUserCommunityMember(uuid, userUuid)
        dto.hasRequestedMembership = communityService.hasUserRequestedMembership(uuid, userUuid)

        return dto
    }

    @POST
    @Path("/")
    fun createCommunity(
        communityRequestDto: CommunityRequestDto
    ): CommunityDetailDto {
        val userUuid = getUserUuid()

        var communityModel = CommunityModel(
            UUID.randomUUID(),
            communityRequestDto.name,
            communityRequestDto.street,
            communityRequestDto.houseNumber,
            communityRequestDto.postalCode,
            communityRequestDto.city,
            userUuid,
            communityRequestDto.radius,
            communityRequestDto.latitude,
            communityRequestDto.longitude,
            communityRequestDto.canBeJoined
        )
        communityModel = communityService.insertCommunity(communityModel)
        return CommunityDetailDto.createWithIsAdmin(communityModel, userUuid)
    }

    @PATCH
    @Path("/{uuid}")
    fun updateCommunity(
        @RestPath uuid: UUID,
        communityRequestDto: CommunityRequestDto
    ): CommunityDetailDto {
        var communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")

        checkUserAdminRight(communityModel)

        communityModel.name = communityRequestDto.name
        communityModel.street = communityRequestDto.street
        communityModel.houseNumber = communityRequestDto.houseNumber
        communityModel.postalCode = communityRequestDto.postalCode
        communityModel.city = communityRequestDto.city
        communityModel.radius = communityRequestDto.radius
        communityModel.canBeJoined  = communityRequestDto.canBeJoined

        communityModel = communityService.updateCommunity(communityModel)

        return CommunityDetailDto.createWithIsAdmin(communityModel, getUserUuid())
    }

    @DELETE
    @Path("/{uuid}")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun deleteCommunity(
        @RestPath uuid: UUID
    ): Response {
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        checkUserAdminRight(communityModel)
        communityService.deleteCommunity(uuid)

        return Response.noContent().build()
    }

    @GET
    @Path("/{uuid}/member")
    fun getCommunityMembers(
        @RestPath uuid: UUID,
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
    ): PageDto<MinimalUserDto> {
        val userModelsPage = userService.getUsersByCommunityUuid(uuid, PageConfig(pageNumber, pageSize))
        return PageDto.of(userModelsPage, ::MinimalUserDto)
    }

    @GET
    @Path("/{uuid}/join")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun joinCommunity(
        @RestPath uuid: UUID,
    ): Response {
        val userUuid = getUserUuid()
        communityService.joinCommunity(userUuid, uuid)
        return Response.noContent().build()
    }

    @GET
    @Path("/{uuid}/leave")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun leaveCommunity(
        @RestPath uuid: UUID,
    ): Response {
        val userUuid = getUserUuid()
        communityService.leaveCommunity(userUuid, uuid)
        return Response.noContent().build()
    }

    @GET
    @Path("/{uuid}/requesting-member")
    fun getCommunityJoinRequests(
        @RestPath uuid: UUID,
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?
    ): PageDto<MinimalUserDto> {
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        checkUserAdminRight(communityModel)
        val userModelsPage = userService.getUsersWithRequestByCommunityUuid(uuid, PageConfig(pageNumber, pageSize))
        return PageDto.of(userModelsPage, ::MinimalUserDto)
    }

    @POST
    @Path("/{uuid}/request/approve")
    fun approveJoinRequests(
        @RestPath uuid: UUID,
        userUuids: List<UUID>
    ): List<MinimalUserDto> {
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        checkUserAdminRight(communityModel)
        communityService.approveRequestsForUsers(uuid, userUuids)
        val userModels = userService.getUsersByUuid(userUuids)
        return userModels.map { MinimalUserDto(it) }
    }

    @POST
    @Path("/{uuid}/request/decline")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun declineJoinRequests(
        @RestPath uuid: UUID,
        userUuids: List<UUID>
    ): Response {
        val communityModel = communityService.getCommunityModel(uuid)
            ?: throw NotFoundException("No community found for UUID: $uuid")
        checkUserAdminRight(communityModel)
        communityService.declineRequestsForUsers(uuid, userUuids)
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