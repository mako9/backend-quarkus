package infrastructure.entity

import common.NotificationType
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.*

@Entity
@Cacheable
@Table(name = "notification")
class Notification : PanacheEntityBase {
    companion object : PanacheCompanion<Notification>

    @Column @Id @Basic @JdbcTypeCode(SqlTypes.CHAR) var uuid: UUID = UUID.randomUUID()

    @Column(name = "user_uuid") @Basic @JdbcTypeCode(SqlTypes.CHAR) lateinit var userUuid: UUID

    @Enumerated(EnumType.STRING)
    @Column lateinit var type: NotificationType

    @Column(name = "booking_uuid") @Basic @JdbcTypeCode(SqlTypes.CHAR) var bookingUuid: UUID? = null

    @Column var read: Boolean = false

    @Column(name = "created_at") lateinit var createdAt: OffsetDateTime

    constructor(userUuid: UUID, type: NotificationType, bookingUuid: UUID?) : this() {
        this.userUuid = userUuid
        this.type = type
        this.bookingUuid = bookingUuid
        this.createdAt = OffsetDateTime.now()
    }

    private constructor()
}
