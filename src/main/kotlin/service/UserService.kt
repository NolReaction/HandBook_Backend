package com.example.service

import com.example.data.model.User
import com.example.data.model.UserDto
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*


class UserService(private val connection: Connection) {

    companion object {
        private const val CREATE_TABLE_USERS = """
            CREATE TABLE IF NOT EXISTS users (
              id SERIAL PRIMARY KEY,
              email VARCHAR(255) UNIQUE NOT NULL,
              password VARCHAR(255) NOT NULL,
              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
              is_verified BOOLEAN NOT NULL DEFAULT FALSE,
              verification_code VARCHAR(255),
              reset_token VARCHAR(255),
              username VARCHAR(255) DEFAULT '',
              avatar VARCHAR(50) NOT NULL
            );
        """
    }

    init {
        val statement = connection.createStatement()
        statement.executeUpdate(CREATE_TABLE_USERS)
    }

    // Функция для поиска пользователя по email
    fun getUserByEmail(email: String): User? {
        val query = """
        SELECT id,
               email,
               password,
               username,
               avatar,
               is_verified,
               verification_code
        FROM users
        WHERE email = ?
    """.trimIndent()

        connection.prepareStatement(query).use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs ->
                return if (rs.next()) {
                    User(
                        id          = rs.getInt("id"),
                        email       = rs.getString("email"),
                        password    = rs.getString("password"),
                        username    = rs.getString("username"),
                        avatar = rs.getString("avatar"),
                        is_verified = rs.getBoolean("is_verified"),
                        verification_code = rs.getString("verification_code")
                    )
                } else null
            }
        }
    }

    // Возвращает профиль вместе с username
    fun getUserById(id: Int): UserDto? {
        val query = """
            SELECT id, email, is_verified, username, avatar
            FROM users
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(query).use { ps ->
            ps.setInt(1, id)
            val rs = ps.executeQuery()
            return if (rs.next()) {
                UserDto(
                    id          = rs.getInt("id"),
                    email       = rs.getString("email"),
                    is_verified = rs.getBoolean("is_verified"),
                    username    = rs.getString("username"),
                    avatar = rs.getString("avatar")
                )
            } else null
        }
    }

    // Обновляет username и возвращает обновлённый профиль
    fun updateUsername(userId: Int, newUsername: String): UserDto? {
        val sql = """
            UPDATE users
            SET username = ?
            WHERE id = ?
            RETURNING id, email, is_verified, username, avatar
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, newUsername)
            ps.setInt(2, userId)
            val rs = ps.executeQuery()
            return if (rs.next()) {
                UserDto(
                    id          = rs.getInt("id"),
                    email       = rs.getString("email"),
                    is_verified = rs.getBoolean("is_verified"),
                    username    = rs.getString("username"),
                    avatar = rs.getString("avatar")
                )
            } else null
        }
    }

    fun registerUser(email: String, rawPassword: String): User? {
        // Проверяем, существует ли пользователь с таким email
        if (getUserByEmail(email) != null) {
            return null
        }

        // Рандомный список аватаров:
        val animals = listOf("bee","beer","deer","fox","monkey","owl","panda","penguin","roe_deer")
        val chosenAvatar = animals.random()

        // Хешируем пароль
        val hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

        // Генерируем уникальный код для подтверждения почты
        val verificationCode = UUID.randomUUID().toString()

        val query = """
        INSERT INTO users (email, password, verification_code, avatar)
        VALUES (?, ?, ?, ?)
        RETURNING id, email, password, username, is_verified, verification_code, avatar
    """.trimIndent()

        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, email)
        preparedStatement.setString(2, hashedPassword)
        preparedStatement.setString(3, verificationCode)
        preparedStatement.setString(4, chosenAvatar)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id          = resultSet.getInt("id"),
                email       = resultSet.getString("email"),
                password    = resultSet.getString("password"),
                username    = resultSet.getString("username"),
                avatar = resultSet.getString("avatar"),
                is_verified = resultSet.getBoolean("is_verified"),
                verification_code = resultSet.getString("verification_code")
            )
        } else {
            null
        }
    }

    fun findByVerificationCode(code: String): User? {
        val query = """
        SELECT id, email, password, username, avatar, is_verified, verification_code 
        FROM users 
        WHERE verification_code = ?
    """.trimIndent()
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, code)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id          = resultSet.getInt("id"),
                email       = resultSet.getString("email"),
                password    = resultSet.getString("password"),
                username    = resultSet.getString("username"),
                avatar = resultSet.getString("avatar"),
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

    fun updateResetToken(userId: Int, resetToken: String): Boolean {
        val query = "UPDATE users SET reset_token = ? WHERE id = ?"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, resetToken)
        preparedStatement.setInt(2, userId)
        val rowsAffected = preparedStatement.executeUpdate()
        return rowsAffected > 0
    }

    fun findByResetToken(token: String): User? {
        val query = """
        SELECT id, email, password, username, avatar, is_verified, verification_code, reset_token 
        FROM users 
        WHERE reset_token = ?
    """.trimIndent()
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, token)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id          = resultSet.getInt("id"),
                email       = resultSet.getString("email"),
                password    = resultSet.getString("password"),
                username    = resultSet.getString("username"),
                avatar = resultSet.getString("avatar"),
                is_verified = resultSet.getBoolean("is_verified"),
                verification_code = resultSet.getString("verification_code")
            )
        } else {
            null
        }
    }

    fun resetPassword(userId: Int, hashedPassword: String): Boolean {
        val query = "UPDATE users SET password = ?, reset_token = NULL WHERE id = ?"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, hashedPassword)
        preparedStatement.setInt(2, userId)
        val rowsUpdated = preparedStatement.executeUpdate()
        return rowsUpdated > 0
    }
}
