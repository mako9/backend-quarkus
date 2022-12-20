package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Type
import java.time.OffsetDateTime
import java.util.*
import javax.persistence.*


@Entity
@Cacheable
class Community : PanacheEntityBase {
    companion object : PanacheCompanion<Community> {
        const val queryAllByNotUserUuid = "SELECT c FROM Community c " +
                "LEFT JOIN UserCommunityRelation ucr " +
                "ON c.uuid = ucr.communityUuid " +
                "WHERE ucr IS NULL OR ucr.userUuid <> ?1"

        const val queryAllByUserUuid = "SELECT c FROM Community c " +
                "JOIN UserCommunityRelation ucr " +
                "ON c.uuid = ucr.communityUuid " +
                "WHERE ucr.userUuid = ?1"
    }

    @Column
    @Id
    @Type(type = "uuid-char")
    lateinit var uuid: UUID

    @Column(length = 100, name = "name")
    lateinit var name: String

    @Column(length = 100)
    var street: String? = null

    @Column(length = 10, name = "house_number")
    var houseNumber: String? = null

    @Column(length = 5, name = "postal_code")
    var postalCode: String? = null

    @Column(length = 100)
    var city: String? = null

    @Column(name = "admin_uuid")
    @Type(type = "uuid-char")
    lateinit var adminUuid: UUID

    @Column
    var radius: Int = 0

    @Column
    var latitude: Double = 0.0

    @Column
    var longitude: Double = 0.0

    @Column(name = "can_be_joined")
    var canBeJoined: Boolean = false

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    @Column(name = "updated_at")
    lateinit var updatedAt: OffsetDateTime

    constructor(
        uuid: UUID,
        name: String,
        street: String?,
        houseNumber: String?,
        postalCode: String?,
        city: String?,
        adminUuid: UUID,
        radius: Int,
        latitude: Double,
        longitude: Double,
        canBeJoined: Boolean
    ) : this() {
        this.uuid = uuid
        this.name = name
        this.street = street
        this.houseNumber = houseNumber
        this.postalCode = postalCode
        this.city = city
        this.adminUuid = adminUuid
        this.radius = radius
        this.latitude = latitude
        this.longitude = longitude
        this.canBeJoined = canBeJoined
        this.createdAt = OffsetDateTime.now()
        this.updatedAt = OffsetDateTime.now()
    }

    private constructor()
}