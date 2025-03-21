package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.data.model.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import kotlinx.html.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*

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
        // Проверяем авторизацию
        post("/login") {
            val loginRequest = call.receive<LoginRequest>()

            // Получаем пользователя из БД по email
            val user = userService.getUserByEmail(loginRequest.email)

            if (user != null) {
                // user.password хранит bcrypt-хеш
                val rawPasswordFromClient = loginRequest.password
                val hashedPasswordFromDB = user.password

                // Проверяем через BCrypt.checkpw
                val passwordMatches = BCrypt.checkpw(rawPasswordFromClient, hashedPasswordFromDB)

                if (passwordMatches) {
                    val token = JWT.create()
                        .withClaim("userId", user.id)
                        .withClaim("userEmail", user.email)
                        .sign(Algorithm.HMAC256("secret"))

                    val loginResponse = LoginResponse(
                        token = token,
                        userId = user.id!!,
                        userEmail = user.email,
                        is_verified = user.is_verified
                    )
                    call.respond(loginResponse)
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }

        // Выполняем регистрацию
        post("/register") {
            val registerRequest = call.receive<RegisterRequest>()

            // Проверяем корректность email
            if (!isValidEmail(registerRequest.email)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid email address")
                return@post
            }

            // Сначала проверяем, существует ли пользователь с таким email
            val existingUser = userService.getUserByEmail(registerRequest.email)
            if (existingUser != null) {
                // Если пользователь уже существует, возвращаем HTTP статус 409 Conflict
                call.respond(HttpStatusCode.Conflict, "User already exists")
                return@post
            }

            // Если пользователь не найден, выполняем регистрацию
            val user = userService.registerUser(registerRequest.email, registerRequest.password)
            if (user != null) {
                // Генерируем token
                val token = JWT.create()
                    .withClaim("userId", user.id)
                    .withClaim("userEmail", user.email)
                    .sign(Algorithm.HMAC256("secret"))

                // Формируем тело письма
                val authServerLink = "http://176.114.71.165:8080/verify?code=${user.verification_code}"
                val authTestLink = "http://10.0.2.2:8080/verify?code=${user.verification_code}"
                val emailBody = """
                    Добро пожаловать в наше приложение!
                    Для подтверждения почты перейдите по ссылке:
                    $authServerLink
                """.trimIndent()

                // Отправляем письмо
                EmailSender.sendEmail(
                    to = registerRequest.email,
                    subject = "Подтверждение регистрации",
                    body = emailBody,
                    from = "authhelper@mail.ru",
                    password = "Eiiws0e7AQ14WtisuJYB"
                )

                // Отправляем ответ клиенту
                val registerResponse = RegisterResponse(
                    token = token,
                    userId = user.id!!,
                    userEmail = user.email,
                    is_verified = false
                )
                call.respond(registerResponse)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Registration failed")
            }
        }

        post("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            val email = request.email
            val user = userService.getUserByEmail(email)

            // Генерируем один и тот же ответ, чтобы не палить, есть ли такой email
            val responseBody = ForgotPasswordMessageResponse("If this email is registered, a reset link has been sent")

            if (user == null) {
                call.respond(HttpStatusCode.OK, responseBody)
                return@post
            }

            val resetToken = UUID.randomUUID().toString()
            userService.updateResetToken(user.id!!, resetToken)

            // Формируем ссылку для сброса пароля
            val resetServerLink = "http://176.114.71.165:8080/reset-password?token=$resetToken"
            val resetTestLink = "http://10.0.2.2:8080/reset-password?token=$resetToken"
            val emailBody = """
                Для восстановления пароля перейдите по следующей ссылке:
                $resetServerLink
            """.trimIndent()

            // Отправляем письмо с помощью EmailSender
            EmailSender.sendEmail(
                to = email,
                subject = "Password Reset Request",
                body = emailBody,
                from = "authhelper@mail.ru",
                password = "Eiiws0e7AQ14WtisuJYB"
            )
            call.respond(HttpStatusCode.OK, responseBody)
        }

        get("/reset-password") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                call.respondText("Missing token", ContentType.Text.Plain)
                return@get
            }
            call.respondHtml {
                head {
                    title { +"Reset Password" }
                }
                body {
                    h1 { +"Reset Your Password" }
                    form(action = "/reset-password", method = FormMethod.post) {
                        hiddenInput { name = "token"; value = token }
                        passwordInput {
                            name = "newPassword"
                            placeholder = "Enter new password"
                        }
                        br
                        submitInput { value = "Reset Password" }
                    }
                }
            }
        }

        post("/reset-password") {
            val params = call.receiveParameters()
            val token = params["token"] ?: return@post call.respondText("Missing token", status = HttpStatusCode.BadRequest)
            val newPassword = params["newPassword"] ?: return@post call.respondText("Missing new password", status = HttpStatusCode.BadRequest)

            // Найдите пользователя по reset-токену. Например, добавьте метод findByResetToken(token: String)
            val user = userService.findByResetToken(token)
            if (user == null) {
                call.respondText("Invalid or expired token", status = HttpStatusCode.BadRequest)
                return@post
            }

            // Хешируем новый пароль
            val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())

            // Обновляем пароль пользователя в базе и удаляем reset-токен
            val updateSuccess = userService.resetPassword(user.id!!, hashedPassword)
            if (updateSuccess) {
                call.respondText("Password has been reset successfully")
            } else {
                call.respondText("Password reset failed", status = HttpStatusCode.InternalServerError)
            }
        }


        get("/verify") {
            val code = call.request.queryParameters["code"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing code")
                return@get
            }
            val user = userService.findByVerificationCode(code)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "Invalid or expired code")
                return@get
            }

            // Если нашли пользователя, ставим is_verified = true и очищаем verification_code
            if (userService.verifyUser(user.id!!)) {
                call.respondText("Email confirmed")
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Verification failed")
            }
        }

        get("/profile") {
            // Извлекаем заголовок Authorization
            val authHeader = call.request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.Unauthorized, "Missing or invalid token")
                return@get
            }
            val token = authHeader.removePrefix("Bearer ").trim()
            try {
                // Декодируем токен
                val jwtVerifier = JWT.require(Algorithm.HMAC256("secret")).build()
                val decodedJWT = jwtVerifier.verify(token)
                // Извлекаем userId из токена
                val userId = decodedJWT.getClaim("userId").asInt()
                // Получаем пользователя из базы данных
                val userDto = userService.getUserById(userId)
                if (userDto == null) {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                } else {
                    call.respond(userDto)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            }
        }

    }
}
