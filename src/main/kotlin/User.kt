package com.example

import com.example.data.model.UserDto
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.util.*

@Serializable
data class User(
    val id: Int? = null,
    val email: String,
    val password: String,
    val is_verified: Boolean,
    val verification_code: String? = null
)

class UserService(private val connection: Connection) {

    companion object {
        private const val CREATE_TABLE_USERS =
            "CREATE TABLE IF NOT EXISTS users (" +
                    "id SERIAL PRIMARY KEY, " +
                    "email VARCHAR(255) UNIQUE NOT NULL," +
                    "password VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(), " +
                    "is_verified BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "verification_code VARCHAR(255)" +
                    ");"
    }

    init {
        val statement = connection.createStatement()
        statement.executeUpdate(CREATE_TABLE_USERS)
    }

    // Функция для поиска пользователя по email
    fun getUserByEmail(email: String): User? {
        val query = "SELECT id, email, password, is_verified FROM users WHERE email = ?"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, email)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                password = resultSet.getString("password"),
                is_verified = resultSet.getBoolean("is_verified")
            )
        } else {
            null
        }
    }

    fun getUserById(id: Int): UserDto? {
        val query = "SELECT id, email, is_verified FROM users WHERE id = ?"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setInt(1, id)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            UserDto(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                is_verified = resultSet.getBoolean("is_verified"),
            )
        } else {
            null
        }
    }

    fun registerUser(email: String, password: String): User? {
        // Сначала проверяем, существует ли пользователь с таким email
        if (getUserByEmail(email) != null) {
            return null
        }

        // Генерируем уникальный код для подтверждения почты
        val verificationCode = UUID.randomUUID().toString()

        val query = """
        INSERT INTO users (email, password, verification_code)
        VALUES (?, ?, ?)
        RETURNING id, email, password, is_verified, verification_code
    """.trimIndent()

        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, email)
        preparedStatement.setString(2, password)
        preparedStatement.setString(3, verificationCode)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                password = resultSet.getString("password"),
                is_verified = resultSet.getBoolean("is_verified"),
                verification_code = resultSet.getString("verification_code")
            )
        } else {
            null
        }
    }

    fun findByVerificationCode(code: String): User? {
        val query = """
        SELECT id, email, password, is_verified, verification_code 
        FROM users 
        WHERE verification_code = ?
    """.trimIndent()
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, code)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                password = resultSet.getString("password"),
                is_verified = resultSet.getBoolean("is_verified"),
                verification_code = resultSet.getString("verification_code")
            )
        } else {
            null
        }
    }

    fun verifyUser(userId: Int): Boolean {
        val query = """
        UPDATE users 
        SET is_verified = TRUE, verification_code = NULL 
        WHERE id = ?
    """.trimIndent()
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setInt(1, userId)
        val updatedRows = preparedStatement.executeUpdate()
        return updatedRows > 0
    }
}
