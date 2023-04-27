package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.*


@Entity
@Cacheable
@Table(name = "item_booking")
class ItemBooking : PanacheEntityBase {
    companion object : PanacheCompanion<ItemBooking>

    @Column
    @Id
    @Basic
    @JdbcTypeCode(SqlTypes.CHAR)
    var uuid: UUID = UUID.randomUUID()

    @Column(name = "item_uuid")
    @Basic
    @JdbcTypeCode(SqlTypes.CHAR)
    lateinit var itemUuid: UUID

    @Column(name = "user_uuid")
    @Basic
    @JdbcTypeCode(SqlTypes.CHAR)
    lateinit var userUuid: UUID

    @Column(name = "start_at")
    lateinit var startAt: OffsetDateTime

    @Column(name = "end_at")
    lateinit var endAt: OffsetDateTime

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    constructor(
        itemUuid: UUID,
        userUuid: UUID,
        startAt: OffsetDateTime,
        endAt: OffsetDateTime
    ) : this() {
        this.itemUuid = itemUuid
        this.userUuid = userUuid
        this.startAt = startAt
        this.endAt = endAt
        this.createdAt = OffsetDateTime.now()
    }

    private constructor()
}