package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes.VARCHAR
import java.time.OffsetDateTime
import java.util.*


@Entity
@Cacheable
@Table(name = "item_image")
class ItemImage : PanacheEntityBase {
    companion object : PanacheCompanion<ItemImage>

    @Column
    @Id
    @JdbcTypeCode(VARCHAR)
    var uuid: UUID = UUID.randomUUID()

    @Column(name = "item_uuid")
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