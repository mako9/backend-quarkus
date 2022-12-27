package api.controller

import api.dto.*
import common.PageConfig
import domain.model.ItemModel
import domain.model.sort.ItemSortBy
import domain.service.CommunityService
import domain.service.ItemService
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

@Path("/user/item")
class ItemController {
    @Inject
    lateinit var itemService: ItemService
    @Inject
    lateinit var communityService: CommunityService
    @Inject
    lateinit var userService: UserService
    @Inject
    lateinit var jwt: JsonWebToken

    @GET
    @Path("/owned")
    fun getItemsOwnedByMe(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
        @QueryParam("sortBy") sortBy: ItemSortBy?,
        @QueryParam("sortDirection") sortDirection: Sort.Direction?,
    ): PageDto<ItemDto> {
        val userUuid = getUserUuid()
        val itemModelsPage = itemService.getItemsPageOfUser(userUuid, PageConfig(pageNumber, pageSize), sortBy, sortDirection)
        return PageDto.of(itemModelsPage, ::ItemDto)
    }

    @GET
    @Path("/my")
    fun getItemsOfMyCommunities(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
        @QueryParam("sortBy") sortBy: ItemSortBy?,
        @QueryParam("sortDirection") sortDirection: Sort.Direction?,
    ): PageDto<ItemDto> {
        val userUuid = getUserUuid()
        val communityPage = communityService.getCommunitiesPageByUser(userUuid, PageConfig(pageSize = 1000), null, null)
        val communityUuids = communityPage.content.map { it.uuid }
        val itemModelsPage = itemService.getItemsPageOfCommunities(communityUuids, userUuid, PageConfig(pageNumber, pageSize), sortBy, sortDirection)
        return PageDto.of(itemModelsPage, ::ItemDto)
    }

    @GET
    @Path("/{uuid}")
    fun getItem(
        @RestPath uuid: UUID,
    ): ItemDetailDto {
        val itemModel = itemService.getItem(uuid) ?: throw NotFoundException("No item found for UUID: $uuid")
        return ItemDetailDto(itemModel)
    }

    @POST
    @Path("/")
    fun createItem(
        itemRequestDto: ItemRequestDto
    ): ItemDetailDto {
        val userUuid = getUserUuid()
        checkCommunity(itemRequestDto.communityUuid)

        var itemModel = ItemModel(
            UUID.randomUUID(),
            itemRequestDto.name,
            itemRequestDto.categories,
            itemRequestDto.street,
            itemRequestDto.houseNumber,
            itemRequestDto.postalCode,
            itemRequestDto.city,
            itemRequestDto.communityUuid,
            userUuid,
            itemRequestDto.isActive,
            availability = itemRequestDto.availability,
            availableUntil = itemRequestDto.availableUntil,
            description = itemRequestDto.description
        )
        itemModel = itemService.insertItem(itemModel)
        return ItemDetailDto(itemModel)
    }

    @PATCH
    @Path("/{uuid}")
    fun updateItem(
        @RestPath uuid: UUID,
        itemRequestDto: ItemRequestDto
    ): ItemDetailDto {
        val userUuid = getUserUuid()
        checkCommunity(itemRequestDto.communityUuid)

        var itemModel = ItemModel(
            uuid,
            itemRequestDto.name,
            itemRequestDto.categories,
            itemRequestDto.street,
            itemRequestDto.houseNumber,
            itemRequestDto.postalCode,
            itemRequestDto.city,
            itemRequestDto.communityUuid,
            userUuid,
            itemRequestDto.isActive,
            availability = itemRequestDto.availability,
            availableUntil = itemRequestDto.availableUntil,
            description = itemRequestDto.description
        )
        itemModel = itemService.updateItem(itemModel)
        return ItemDetailDto(itemModel)
    }

    @DELETE
    @Path("/{uuid}")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun deleteItem(
        @RestPath uuid: UUID
    ): Response {
        itemService.deleteItem(uuid, getUserUuid())

        return Response.noContent().build()
    }

    private fun getUserUuid(): UUID {
        val mail = jwt.getClaim<String>(Claims.email)
        val userModel = userService.getUserByMail(mail) ?: throw NotFoundException("No user for mail $mail")
        return userModel.uuid
    }

    private fun checkCommunity(communityUuid: UUID)  {
        communityService.getCommunityModel(communityUuid)
            ?: throw NotFoundException("No community found for UUID: $communityUuid")
    }
}