package com.example

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class User(
    val id: Int? = null,
    val email: String,
    val password: String
)

class UserService(private val connection: Connection) {

    companion object {
        private const val CREATE_TABLE_USERS =
            "CREATE TABLE IF NOT EXISTS users (" +
                    "id SERIAL PRIMARY KEY, " +
                    "email VARCHAR(255) UNIQUE NOT NULL," +
                    "password VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(), " +
                    "avatar BYTEA" +
                    ");"
    }

    init {
        val statement = connection.createStatement()
        statement.executeUpdate(CREATE_TABLE_USERS)
    }

    // Функция для поиска пользователя по email
    fun getUserByEmail(email: String): User? {
        val query = "SELECT id, email, password FROM users WHERE email = ?"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, email)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                password = resultSet.getString("password")
            )
        } else {
            null
        }
    }

    // Функция для регистрации пользователя
    fun registerUser(email: String, password: String): User? {
        // Сначала проверяем, существует ли пользователь с таким email
        if (getUserByEmail(email) != null) {
            return null
        }

        val query = "INSERT INTO users (email, password) VALUES (?, ?) RETURNING id, email, password"
        val preparedStatement = connection.prepareStatement(query)
        preparedStatement.setString(1, email)
        preparedStatement.setString(2, password)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            User(
                id = resultSet.getInt("id"),
                email = resultSet.getString("email"),
                password = resultSet.getString("password")
            )
        } else {
            null
        }
    }
}
