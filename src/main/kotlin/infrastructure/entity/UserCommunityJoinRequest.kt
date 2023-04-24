package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.*

@Entity
@Cacheable
@Table(name = "user_community_join_request")
@IdClass(UserCommunityJoinRequestId::class)
class UserCommunityJoinRequest : PanacheEntityBase {
    companion object : PanacheCompanion<UserCommunityJoinRequest>

    @Column(name = "user_uuid")
    @Id
    @UuidGenerator
    lateinit var userUuid: UUID

    @Column(name = "community_uuid")
    @Id
    @UuidGenerator
    lateinit var communityUuid: UUID

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    constructor(
        userUuid: UUID,
        communityUuid: UUID
    ) : this() {
        this.userUuid = userUuid
        this.communityUuid = communityUuid
        this.createdAt = OffsetDateTime.now()
    }

    constructor()
}

private data class UserCommunityJoinRequestId(
    val userUuid: UUID = UUID.randomUUID(),
    val communityUuid: UUID = UUID.randomUUID()
) : Serializable