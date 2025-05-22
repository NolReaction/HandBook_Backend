package com.example.service

import com.example.data.model.User
import com.example.data.model.UserDto
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.regex.Pattern


class UserService(val connection: Connection) {

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
              username VARCHAR(12) NOT NULL DEFAULT '',
              avatar VARCHAR(50) NOT NULL
            );
        """
        private const val CREATE_UNIQUE_USERNAME_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username
            ON users(username);
        """
        // regex: только A–Z, a–z, 0–9, от 4 до 12 символов
        private val USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9]{4,12}\$")
    }

    init {
        // создаём таблицу и индекс
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(CREATE_TABLE_USERS)
            stmt.executeUpdate(CREATE_UNIQUE_USERNAME_INDEX)
        }
    }

    /** Находит пользователя по email (для входа) */
    fun getUserByEmail(email: String): User? {
        val sql = """
            SELECT id, email, password, username, avatar, is_verified, verification_code
            FROM users WHERE email = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs ->
                return if (rs.next()) {
                    User(
                        id = rs.getInt("id"),
                        email = rs.getString("email"),
                        password = rs.getString("password"),
                        username = rs.getString("username"),
                        avatar = rs.getString("avatar"),
                        is_verified = rs.getBoolean("is_verified"),
                        verification_code = rs.getString("verification_code")
                    )
                } else null
            }
        }
    }

    /** Возвращает профиль (DTO для клиента) */
    fun getUserById(id: Int): UserDto? {
        val sql = """
            SELECT id, email, is_verified, username, avatar
            FROM users WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, id)
            ps.executeQuery().use { rs ->
                return if (rs.next()) {
                    UserDto(
                        id = rs.getInt("id"),
                        email = rs.getString("email"),
                        username = rs.getString("username"),
                        avatar = rs.getString("avatar"),
                        is_verified = rs.getBoolean("is_verified")
                    )
                } else null
            }
        }
    }

    /**
     * Обновляет username:
     * 1) валидация формата
     * 2) проверка уникальности
     * 3) обновление и возврат нового профиля
     *
     * @throws IllegalArgumentException если формат неверный
     * @throws UsernameAlreadyExistsException если имя занято
     */
    fun updateUsername(userId: Int, newUsername: String): UserDto {
        // 1) формат
        if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
            throw IllegalArgumentException(
                "Username must be 4–12 characters, letters and digits only"
            )
        }

        // 2) уникальность (не включает себя)
        val checkSql = "SELECT 1 FROM users WHERE username = ? AND id <> ?"
        connection.prepareStatement(checkSql).use { ps ->
            ps.setString(1, newUsername)
            ps.setInt(2, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    throw UsernameAlreadyExistsException("Username '$newUsername' is already taken")
                }
            }
        }

        // 3) обновление
        val sql = """
            UPDATE users
            SET username = ?
            WHERE id = ?
            RETURNING id, email, is_verified, username, avatar
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, newUsername)
            ps.setInt(2, userId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    return UserDto(
                        id = rs.getInt("id"),
                        email = rs.getString("email"),
                        username = rs.getString("username"),
                        avatar = rs.getString("avatar"),
                        is_verified = rs.getBoolean("is_verified")
                    )
                }
                throw RuntimeException("Failed to update username")
            }
        }
    }

    /** Проверяет, существует ли в БД такой username (кроме указанного userId) */
    fun isUsernameTaken(username: String, excludeUserId: Int? = null): Boolean {
        val sql = if (excludeUserId == null) {
            "SELECT 1 FROM users WHERE username = ?"
        } else {
            "SELECT 1 FROM users WHERE username = ? AND id <> ?"
        }
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, username)
            excludeUserId?.let { ps.setInt(2, it) }
            ps.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    /**
     * Регистрирует пользователя, используя уже сгенерированный verificationCode,
     * на переданном connection (без автокоммита).
     */
    fun registerUserWithCode(
        connection: Connection,
        email: String,
        rawPassword: String,
        verificationCode: String
    ): User {
        // Проверка, что email ещё не занят
        if (getUserByEmail(email) != null) {
            throw EmailAlreadyExistsException("Email '$email' is already registered")
        }

        // Генерация уникального username
        val localPart  = email.substringBefore("@")
        val sanitized  = localPart.replace(Regex("[^A-Za-z0-9]"), "")
        val base       = sanitized.takeIf { it.isNotBlank() }?.take(12) ?: "user"
        val username   = generateUniqueUsername(base)

        // Остальные поля
        val animals      = listOf("bee","beer","deer","fox","monkey","owl","panda","penguin","roe_deer")
        val chosenAvatar = animals.random()
        val hashedPw     = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

        // Вставка
        val sql = """
        INSERT INTO users (email, password, verification_code, avatar, username)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id, email, password, username, avatar, is_verified, verification_code
    """.trimIndent()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, email)
            ps.setString(2, hashedPw)
            ps.setString(3, verificationCode)   // используем переданный
            ps.setString(4, chosenAvatar)
            ps.setString(5, username)

            ps.executeQuery().use { rs ->
                if (!rs.next()) throw RuntimeException("Registration failed")

                return User(
                    id                = rs.getInt("id"),
                    email             = rs.getString("email"),
                    password          = rs.getString("password"),
                    username          = rs.getString("username"),
                    avatar            = rs.getString("avatar"),
                    is_verified       = rs.getBoolean("is_verified"),
                    verification_code = rs.getString("verification_code")
                )
            }
        }
    }


    private fun generateUniqueUsername(base: String): String {
        var candidate = base
        var suffix = 0
        while (isUsernameTaken(candidate, excludeUserId = null)) {
            suffix++
            // отсекаем, чтобы не превысить 12 знаков
            val trimmedBase = if (base.length + suffix.toString().length > 12)
                base.take(12 - suffix.toString().length)
            else
                base
            candidate = "$trimmedBase$suffix"
        }
        return candidate
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

    fun deleteUser(userId: Int): Boolean {
        val sql = "DELETE FROM users WHERE id = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, userId)
            return ps.executeUpdate() > 0
        }
    }

    /** Исключение для дублирующегося username */
    class UsernameAlreadyExistsException(message: String) : RuntimeException(message)

    /** Исключение для дублирующегося email при регистрации */
    class EmailAlreadyExistsException(message: String) : RuntimeException(message)
}
