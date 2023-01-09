package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Type
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.*


@Entity
@Cacheable
@Table(name = "item_image")
class ItemImage : PanacheEntityBase {
    companion object : PanacheCompanion<ItemImage>

    @Column
    @Id
    @Type(type = "uuid-char")
    var uuid: UUID = UUID.randomUUID()

    @Column(name = "item_uuid")
    @Type(type = "uuid-char")
    lateinit var itemUuid: UUID

    @Column
    lateinit var path: String

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    constructor(
        itemUuid: UUID,
        path: String
    ) : this() {
        this.itemUuid = itemUuid
        this.path = path
        this.createdAt = OffsetDateTime.now()
    }

    private constructor()
}