package com.example.service

import com.example.data.model.PlaceDto
import java.sql.Connection

class PlaceService(private val connection: Connection) {

    companion object {
        private const val CREATE_TABLE_PLACES = """
            CREATE TABLE IF NOT EXISTS places (
              id SERIAL PRIMARY KEY,
              name VARCHAR(255) NOT NULL,
              rating REAL NOT NULL,
              description TEXT
            );
        """

        // Пара тестовых записей
        private const val SEED_DATA = """
          INSERT INTO places (name, rating, description)
          SELECT 'Кафе "Уют"', 4.2, 'Уютное место с отличным кофе'
          WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Кафе "Уют"');
          
          INSERT INTO places (name, rating, description)
          SELECT 'Парк "Солнечный"', 3.8, 'Зелёный парк для прогулок'
          WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Парк "Солнечный"');
        """
    }

    init {
        // при инициализации сервиса — создаём таблицу, если ещё нет
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(CREATE_TABLE_PLACES)
            stmt.executeUpdate(SEED_DATA)
        }
    }

    fun getAll(): List<PlaceDto> {
        val sql = "SELECT id, name, rating, description FROM places ORDER BY id"
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            val list = mutableListOf<PlaceDto>()
            while (rs.next()) {
                list += PlaceDto(
                    id          = rs.getInt("id"),
                    name        = rs.getString("name"),
                    rating      = rs.getFloat("rating"),
                    description = rs.getString("description")
                )
            }
            return list
        }
    }

    // сюда же можно добавить методы insert/update/delete...
}