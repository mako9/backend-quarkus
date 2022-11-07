package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Type
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.*

@Entity
@Cacheable
@Table(name = "user_community_join_request")
@IdClass(UserCommunityJoinRequestId::class)
class UserCommunityJoinRequest : PanacheEntityBase {
    companion object : PanacheCompanion<UserCommunityJoinRequest>

    @Column(name = "user_uuid")
    @Id
    @Type(type = "uuid-char")
    lateinit var userUuid: UUID

    @Column(name = "community_uuid")
    @Id
    @Type(type = "uuid-char")
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
): Serializable