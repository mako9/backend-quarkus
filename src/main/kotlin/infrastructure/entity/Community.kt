package infrastructure.entity

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*


@Entity
@Cacheable
@NamedQueries(
    NamedQuery(name = "Community.getByUserUuid", query = "select c from Community c join UserCommunityRelation ucr on c.uuid = ucr.communityUuid where ucr.userUuid = :userUuid order by name ASC"),
    NamedQuery(name = "Community.getByUuid", query = "from Community c where c.uuid = ?1"),
    NamedQuery(name = "Community.deleteByUuid", query = "delete from Community c where c.uuid = ?1")
)
class Community : PanacheEntityBase {
    companion object : PanacheCompanion<Community>

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
    lateinit var adminUuid: UUID

    @Column
    var radius: Int = 0

    constructor(
        uuid: UUID,
        name: String,
        street: String?,
        houseNumber: String?,
        postalCode: String?,
        city: String?,
        adminUuid: UUID,
        radius: Int
    ) : this() {
        this.uuid = uuid
        this.name = name
        this.street = street
        this.houseNumber = houseNumber
        this.postalCode = postalCode
        this.city = city
        this.adminUuid = adminUuid
        this.radius = radius
    }

    constructor()
}