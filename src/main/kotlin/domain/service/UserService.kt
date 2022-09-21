package domain.service

import common.UserRole
import domain.model.UserModel
import infrastructure.entity.User
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.transaction.Transactional
import javax.ws.rs.WebApplicationException

@ApplicationScoped
@Transactional
class UserService {

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

    fun updateUser(mail: String, firstName: String?, lastName: String?, street: String?, houseNumber: String?, postalCode: String?, city: String?): UserModel {
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