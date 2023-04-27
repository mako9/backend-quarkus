package infrastructure.entity

import com.vladmihalcea.hibernate.type.array.EnumArrayType
import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType
import common.UserRole
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import java.sql.Types.CHAR
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "user", schema = "public")
@Cacheable
@NamedQueries(
    NamedQuery(
        name = "User.getByCommunityUuid",
        query = "select u from User u join UserCommunityRelation ucr on u.uuid = ucr.userUuid where ucr.communityUuid = :communityUuid order by u.lastName ASC"
    ),
    NamedQuery(
        name = "User.getWithRequestByCommunityUuid",
        query = "select u from User u join UserCommunityJoinRequest ucjr on u.uuid = ucjr.userUuid where ucjr.communityUuid = :communityUuid order by u.lastName ASC"
    )
)
class User : PanacheEntityBase {
    companion object : PanacheCompanion<User>

    @Column
    @Id
    @Basic
    @JdbcTypeCode(CHAR)
    lateinit var uuid: UUID

    @Column(length = 100, name = "first_name")
    lateinit var firstName: String

    @Column(length = 100, name = "last_name")
    lateinit var lastName: String

    @Column(length = 255, unique = true)
    lateinit var mail: String

    @Column(length = 100)
    var street: String? = null

    @Column(length = 10, name = "house_number")
    var houseNumber: String? = null

    @Column(length = 5, name = "postal_code")
    var postalCode: String? = null

    @Column(length = 100)
    var city: String? = null

    @Column(name = "created_at")
    lateinit var createdAt: OffsetDateTime

    @Column(name = "updated_at")
    lateinit var updatedAt: OffsetDateTime

    @Column
    @Type(
        value = EnumArrayType::class,
        parameters = [Parameter(value = "user_role", name = AbstractArrayType.SQL_ARRAY_TYPE)]
    )
    lateinit var roles: Array<UserRole>

    constructor(
        uuid: UUID,
        firstName: String,
        lastName: String,
        mail: String,
        street: String?,
        houseNumber: String?,
        postalCode: String?,
        city: String?,
        roles: List<UserRole>
    ) : this() {
        this.uuid = uuid
        this.firstName = firstName
        this.lastName = lastName
        this.mail = mail
        this.street = street
        this.houseNumber = houseNumber
        this.postalCode = postalCode
        this.city = city
        this.roles = roles.toTypedArray()
        this.createdAt = OffsetDateTime.now()
        this.updatedAt = OffsetDateTime.now()
    }

    private constructor()
}