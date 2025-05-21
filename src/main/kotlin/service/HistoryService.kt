// src/main/kotlin/com/example/service/HistoryService.kt
package com.example.service

import com.example.data.model.PlaceDto
import com.example.data.model.HistoryEntryDto
import java.sql.Connection

class HistoryService(private val connection: Connection) {

    companion object {
        // DDL для таблицы history
        private const val CREATE_TABLE_HISTORY = """
            CREATE TABLE IF NOT EXISTS history (
              id SERIAL PRIMARY KEY,
              user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              place_id INTEGER NOT NULL REFERENCES places(id),
              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
            );
        """
    }

    init {
        // при старте приложения создаём таблицу, если её ещё нет
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(CREATE_TABLE_HISTORY)
        }
    }

    /**
     * Записать факт просмотра placeId пользователем userId
     */
    fun record(userId: Int, placeId: Int) {
        val sql = "INSERT INTO history (user_id, place_id) VALUES (?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, userId)
            stmt.setInt(2, placeId)
            stmt.executeUpdate()
        }
    }

    /**
     * Вернуть список записей истории для userId:
     * для каждой — объект места и время просмотра
     */
    fun getAll(userId: Int): List<HistoryEntryDto> {
        val sql = """
            SELECT h.created_at, p.id, p.name, p.rating, p.description
              FROM history h
              JOIN places p ON p.id = h.place_id
             WHERE h.user_id = ?
             ORDER BY h.created_at DESC
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, userId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<HistoryEntryDto>()
            while (rs.next()) {
                val place = PlaceDto(
                    id          = rs.getInt("id"),
                    name        = rs.getString("name"),
                    rating      = rs.getFloat("rating"),
                    description = rs.getString("description")
                )
                list += HistoryEntryDto(
                    place     = place,
                    createdAt = rs.getTimestamp("created_at")
                        .toInstant()
                        .toString()
                )
            }
            return list
        }
    }
}
