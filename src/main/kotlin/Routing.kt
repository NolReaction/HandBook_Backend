package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.data.model.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection

fun Application.configureRouting() {

    // Создаем подключение к БД и инициализируем UserService один раз
    val dbConnection: Connection = connectToPostgres(embedded = false)
    val userService = UserService(dbConnection)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }

    install(ContentNegotiation) {
        json() // настройка JSON сериализации
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        post("/login") {
            val loginRequest = call.receive<LoginRequest>()

            // Получаем пользователя из БД по email
            val user = userService.getUserByEmail(loginRequest.email)

            if (user != null && user.password == loginRequest.password) {
                val token = JWT.create()
                    .withClaim("userId", user.id)
                    .withClaim("userEmail", user.email)
                    .sign(Algorithm.HMAC256("secret"))

                val loginResponse = LoginResponse(
                    token = token,
                    userId = user.id!!,
                    userEmail = user.email
                )
                call.respond(loginResponse)
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }
    }
}
