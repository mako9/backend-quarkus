package infrastructure.entity

import com.vladmihalcea.hibernate.type.array.EnumArrayType
import common.ItemCategory
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.*


@Entity
@Cacheable
@TypeDef(name = "item_category_enum", typeClass = EnumArrayType::class,
    defaultForType = Array<ItemCategory>::class,
    parameters = [
        Parameter(
            name = EnumArrayType.SQL_ARRAY_TYPE,
            value = "item_category"
        )
    ]
)
class Item : PanacheEntityBase {
    companion object : PanacheCompanion<Item>

    @Column
    @Id
    @Type(type = "uuid-char")
    lateinit var uuid: UUID

    @Column
    lateinit var name: String

    @Column
    @Type(type = "item_category_enum")
    lateinit var categories: Array<ItemCategory>

    @Column
    var street: String? = null

    @Column(name = "house_number")
    var houseNumber: String? = null

    @Column(length = 5, name = "postal_code")
    var postalCode: String? = null

    @Column
    var city: String? = null

    @Column(name = "community_uuid")
    @Type(type = "uuid-char")
    lateinit var communityUuid: UUID

    @Column(name = "user_uuid")
    @Type(type = "uuid-char")
    lateinit var userUuid: UUID

    @Column(name = "is_active")
    var isActive: Boolean = true

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    @Column(name = "updated_at")
    lateinit var updatedAt: OffsetDateTime

    @Column(name = "availability_json")
    var availability: String? = null

    @Column(name = "available_until")
    var availableUntil: OffsetDateTime? = null

    @Column
    var description: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    var community: Community? = null

    constructor(
        uuid: UUID,
        name: String,
        categories: List<ItemCategory>,
        street: String?,
        houseNumber: String?,
        postalCode: String?,
        city: String?,
        communityUuid: UUID,
        userUuid: UUID,
        isActive: Boolean,
        availability: String?,
        availableUntil: OffsetDateTime?,
        description: String?
    ) : this() {
        this.uuid = uuid
        this.name = name
        this.categories = categories.toTypedArray()
        this.street = street
        this.houseNumber = houseNumber
        this.postalCode = postalCode
        this.city = city
        this.communityUuid = communityUuid
        this.userUuid = userUuid
        this.isActive = isActive
        this.createdAt = OffsetDateTime.now()
        this.updatedAt = OffsetDateTime.now()
        this.availability = availability
        this.availableUntil = availableUntil
        this.description = description
    }

    private constructor()
}