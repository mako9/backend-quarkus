package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.io.Serializable
import java.sql.Types.VARCHAR
import java.time.OffsetDateTime
import java.util.*

@Entity
@Cacheable
@Table(name = "user_community_relation")
@IdClass(UserCommunityRelationId::class)
class UserCommunityRelation : PanacheEntityBase {
    companion object : PanacheCompanion<UserCommunityRelation>

    @Column(name = "user_uuid")
    @Id
    @JdbcTypeCode(VARCHAR)
    lateinit var userUuid: UUID

    @Column(name = "community_uuid")
    @Id
    @JdbcTypeCode(VARCHAR)
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

private data class UserCommunityRelationId(
    val userUuid: UUID = UUID.randomUUID(),
    val communityUuid: UUID = UUID.randomUUID()
) : Serializable