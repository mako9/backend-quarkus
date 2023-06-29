package domain.model

import infrastructure.entity.UserCommunityJoinRequest
import java.time.OffsetDateTime
import java.util.*

data class UserCommunityJoinRequestModel(
    val userUuid: UUID,
    val communityUuid: UUID,
    val createdAt: OffsetDateTime
) {
    constructor(userCommunityJoinRequest: UserCommunityJoinRequest) : this(
        userUuid = userCommunityJoinRequest.userUuid,
        communityUuid = userCommunityJoinRequest.communityUuid,
        createdAt = userCommunityJoinRequest.createdAt
    )
}