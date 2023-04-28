package api.controller

import api.dto.*
import common.PageConfig
import domain.model.ItemModel
import domain.model.sort.ItemBookingSortBy
import domain.model.sort.ItemSortBy
import domain.service.CommunityService
import domain.service.ItemService
import domain.service.UserService
import io.quarkus.panache.common.Sort
import jakarta.inject.Inject
import jakarta.validation.constraints.Min
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.apache.http.HttpStatus
import org.eclipse.microprofile.jwt.Claims
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import org.hibernate.validator.constraints.Range
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import org.jboss.resteasy.reactive.ResponseStatus
import org.jboss.resteasy.reactive.RestPath
import java.net.URI
import java.util.*


@Path("/user/item")
@SecurityRequirement(name = "bearerAuth")
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
        val itemModelsPage =
            itemService.getItemsPageOfUser(userUuid, PageConfig(pageNumber, pageSize), sortBy, sortDirection)
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
        val itemModelsPage = itemService.getItemsPageOfCommunities(
            communityUuids,
            userUuid,
            PageConfig(pageNumber, pageSize),
            sortBy,
            sortDirection
        )
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
            availability = itemRequestDto.availability.map { it.toTimeIntervalModel() },
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
            availability = itemRequestDto.availability.map { it.toTimeIntervalModel() },
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

    @GET
    @Path("/{uuid}/image")
    fun getItemImageUuids(
        @RestPath uuid: UUID
    ): List<UUID> {
        return itemService.getImageUuids(uuid)
    }

    @POST
    @Path("/{uuid}/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(HttpStatus.SC_CREATED)
    @RequestBody(
        content = [Content(
            mediaType = MediaType.MULTIPART_FORM_DATA,
            schema = Schema(implementation = MultipartDto::class)
        )]
    )
    fun uploadImages(
        @RestPath uuid: UUID,
        @MultipartForm multipartDto: MultipartDto
    ): Response {
        val imageUuid = itemService.saveItemImage(uuid, multipartDto.file, getUserUuid())

        return Response.created(URI.create("/image/${imageUuid}")).build()
    }

    @GET
    @Path("/image/{uuid}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getItemImage(
        @RestPath uuid: UUID
    ): Response? {
        val file = itemService.getItemImageFile(uuid)
        val response: Response.ResponseBuilder = Response.ok(file)
        response.header("Content-Disposition", "attachment; filename=$file")
        return response.build()
    }

    @DELETE
    @Path("/image/{uuid}")
    @ResponseStatus(HttpStatus.SC_NO_CONTENT)
    fun deleteItemImage(
        @RestPath uuid: UUID
    ): Response {
        itemService.deleteItemImage(uuid, getUserUuid())

        return Response.noContent().build()
    }

    @GET
    @Path("/booking")
    fun getMyItemBookings(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
        @QueryParam("sortBy") sortBy: ItemBookingSortBy?,
        @QueryParam("sortDirection") sortDirection: Sort.Direction?,
    ): PageDto<ItemBookingDto> {
        val userUuid = getUserUuid()
        val itemBookingsPage = itemService.getItemBookingsPageOfUser(
            userUuid,
            PageConfig(pageNumber, pageSize),
            sortBy,
            sortDirection
        )
        return PageDto.of(itemBookingsPage, ::ItemBookingDto)
    }

    @GET
    @Path("/{uuid}/booking")
    fun getItemBookingsOfItem(
        @RestPath uuid: UUID,
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
        @QueryParam("sortBy") sortBy: ItemBookingSortBy?,
        @QueryParam("sortDirection") sortDirection: Sort.Direction?,
    ): PageDto<ItemBookingDto> {
        val userUuid = getUserUuid()
        val itemBookingsPage = itemService.getItemBookingsPageOfItem(
            userUuid,
            uuid,
            PageConfig(pageNumber, pageSize),
            sortBy,
            sortDirection
        )
        return PageDto.of(itemBookingsPage, ::ItemBookingDto)
    }

    @GET
    @Path("/booking/{uuid}")
    fun getItemBooking(
        @RestPath uuid: UUID
    ): ItemBookingDto {
        val userUuid = getUserUuid()
        val itemBookingModel = itemService.getItemBooking(userUuid, uuid)
        return ItemBookingDto(itemBookingModel)
    }

    @POST
    @ResponseStatus(HttpStatus.SC_CREATED)
    @Path("/{uuid}/booking")
    fun bookItem(
        @RestPath uuid: UUID,
        itemBookingRequestDto: ItemBookingRequestDto
    ): Response {
        val userUuid = getUserUuid()

        val itemBookingModel = itemService.bookItem(
            itemUuid = uuid,
            userUuid = userUuid,
            startAt = itemBookingRequestDto.startAt,
            endAt = itemBookingRequestDto.endAt
        )
        return Response.created(URI.create("/booking/${itemBookingModel.uuid}")).build()
    }

    private fun getUserUuid(): UUID {
        val mail = jwt.getClaim<String>(Claims.email)
        val userModel = userService.getUserByMail(mail) ?: throw NotFoundException("No user for mail $mail")
        return userModel.uuid
    }

    private fun checkCommunity(communityUuid: UUID) {
        communityService.getCommunityModel(communityUuid)
            ?: throw NotFoundException("No community found for UUID: $communityUuid")
    }
}