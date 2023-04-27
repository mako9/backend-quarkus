package domain.service

import common.PageConfig
import common.UserRole
import domain.model.PageModel
import domain.model.UserModel
import infrastructure.entity.User
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Parameters
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.ws.rs.WebApplicationException
import java.util.*

@ApplicationScoped
@Transactional
class UserService {

    fun getUserByUuid(uuid: UUID): UserModel? {
        val user = User.find("uuid", uuid).firstResult() ?: return null
        return UserModel(user)
    }

    fun getUsersByUuid(uuids: List<UUID>): List<UserModel> {
        val users = User.find("uuid IN ?1", uuids).list()
        return users.map { UserModel(it) }
    }

    fun getUserByMail(mail: String): UserModel? {
        val user = User.find("mail", mail).firstResult() ?: return null
        return UserModel(user)
    }

    fun createUser(mail: String, firstName: String, lastName: String, roles: List<UserRole>): UserModel {
        val userModel = UserModel(
            uuid = UUID.randomUUID(),
            firstName = firstName,
            lastName = lastName,
            mail = mail,
            roles = roles
        )
        val user = createUserEntityFromUserModel(userModel)
        user.persistAndFlush()
        return userModel
    }

    fun updateUser(
        mail: String,
        firstName: String?,
        lastName: String?,
        street: String?,
        houseNumber: String?,
        postalCode: String?,
        city: String?
    ): UserModel {
        val user = User.find("mail", mail).firstResult()
            ?: throw WebApplicationException("No user exists with mail: $mail", 404)
        user.firstName = firstName ?: user.firstName
        user.lastName = lastName ?: user.lastName
        user.street = street
        user.houseNumber = houseNumber
        user.postalCode = postalCode
        user.city = city
        user.persist()
        return UserModel(user)
    }

    fun getUsersByCommunityUuid(communityUuid: UUID, pageConfig: PageConfig): PageModel<UserModel> {
        val query = User
            .find(
                query = "#User.getByCommunityUuid",
                Parameters.with("communityUuid", communityUuid)
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::UserModel)
    }

    fun getUsersWithRequestByCommunityUuid(communityUuid: UUID, pageConfig: PageConfig): PageModel<UserModel> {
        val query = User
            .find(
                query = "#User.getWithRequestByCommunityUuid",
                Parameters.with("communityUuid", communityUuid)
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::UserModel)
    }

    private fun createUserEntityFromUserModel(userModel: UserModel): User {
        return User(
            userModel.uuid,
            userModel.firstName,
            userModel.lastName,
            userModel.mail,
            userModel.street,
            userModel.houseNumber,
            userModel.postalCode,
            userModel.city,
            userModel.roles
        )
    }
}