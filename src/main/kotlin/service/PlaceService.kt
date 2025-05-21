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

        // Тестовые записи
        private const val SEED_DATA = """
            INSERT INTO places (name, rating, description)
            SELECT 'Кафе "Уют"', 4.2, 'Уютное место с отличным кофе'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Кафе "Уют"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Парк "Солнечный"', 3.8, 'Зелёный парк для прогулок'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Парк "Солнечный"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Ресторан "Лакомка"', 4.7, 'Гастрономический ресторан итальянской кухни'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Ресторан "Лакомка"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Музей современного искусства"', 4.4, 'Экспозиции российских и зарубежных художников'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Музей современного искусства"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Книжная лавка "Читай-город"', 4.1, 'Уютная атмосфера и редкие издания'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Книжная лавка "Читай-город"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Кинотеатр "Оскар"', 4.5, 'Премьеры мирового кино в формате 4K'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Кинотеатр "Оскар"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Аквариум "Глубины"', 4.3, 'Морские обитатели со всего мира'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Аквариум "Глубины"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Зоопарк "Лесная сказка"', 4.0, 'Редкие виды животных и интерактивные программы'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Зоопарк "Лесная сказка"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Библиотека "Знание"', 4.6, 'Современные залы для чтения и работы'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Библиотека "Знание"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Коворкинг "Планета"', 4.2, 'Комфортные рабочие места и переговорные комнаты'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Коворкинг "Планета"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Фитнес-центр "Атлет"', 4.4, 'Современные тренажёры и групповые занятия'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Фитнес-центр "Атлет"');
        
            INSERT INTO places (name, rating, description)
            SELECT 'Пляж "Золотой берег"', 4.8, 'Белый песок и чистая вода'
            WHERE NOT EXISTS (SELECT 1 FROM places WHERE name = 'Пляж "Золотой берег"');
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