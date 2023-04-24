package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Type
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.*


@Entity
@Cacheable
@Table(name = "item_booking")
class ItemBooking : PanacheEntityBase {
    companion object : PanacheCompanion<ItemBooking>

    @Column
    @Id
    @Type(type = "uuid-char")
    var uuid: UUID = UUID.randomUUID()

    @Column(name = "item_uuid")
    @Type(type = "uuid-char")
    lateinit var itemUuid: UUID

    @Column(name = "user_uuid")
    @Type(type = "uuid-char")
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