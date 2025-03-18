package com.example

import io.ktor.server.application.*
import java.sql.Connection
import java.sql.DriverManager

/*
fun Application.configureDatabases() {
    // Создаем подключение к базе данных
    val dbConnection: Connection = connectToPostgres(embedded = false)

    routing {

    }
}
*/

fun Application.connectToPostgres(embedded: Boolean): Connection {
    // Загрузка драйвера
    Class.forName("org.postgresql.Driver")

    // Если embedded true, то используем H2 для тестов
    if (embedded) {
        log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    }
    else {
        // Строка подключения к базе
        val url = environment.config.property("postgres.url").getString()
        log.info("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}
