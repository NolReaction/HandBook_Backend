package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.data.model.*
import com.example.functions.isValidEmail
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.plugins.*
import kotlinx.html.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*
import com.example.functions.isBlocked
import com.example.functions.recordLoginAttempt


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

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6 && password.any { it.isDigit() } && password.any { it.isLetter() }
    }

    routing {
        // Проверяем авторизацию
        post("/login") {
            // Здесь можно, например, получить IP адрес из заголовков:
            val ipAddress = call.request.origin.remoteHost

            if (isBlocked(ipAddress)) {
                call.respond(HttpStatusCode.TooManyRequests, "Too many failed attempts. Please try again later.")
                return@post
            }

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
                recordLoginAttempt(ipAddress)
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
                    <html>
                      <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                        <div style="background-color: #fff; padding: 20px; border-radius: 8px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                          <h2 style="color: #333;">Добро пожаловать в наше приложение!</h2>
                          <p style="color: #555;">Для подтверждения почты нажмите на кнопку ниже:</p>
                          <a href="$authServerLink" style="display: inline-block; padding: 10px 20px; color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px; margin-top: 20px;">Подтвердить почту</a>
                        </div>
                      </body>
                    </html>
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
                <html>
                  <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;">
                    <div style="background-color: #fff; padding: 20px; border-radius: 8px; text-align: center; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);">
                      <h2 style="color: #333;">Password Reset Request</h2>
                      <p style="color: #555;">To reset your password, please click the button below:</p>
                      <a href="$resetServerLink" style="display: inline-block; padding: 10px 20px; margin-top: 20px; color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px;">Reset Password</a>
                    </div>
                  </body>
                </html>
            """.trimIndent()


            // Отправляем письмо с помощью EmailSender
            EmailSender.sendEmail(
                to = email,
                subject = "Запрос на восстановление пароля",
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
                    style {
                        unsafe {
                            raw("""
                        body {
                            font-family: Arial, sans-serif;
                            background-color: #f4f4f4;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            height: 100vh;
                            margin: 0;
                        }
                        .container {
                            background-color: #fff;
                            padding: 2rem;
                            border-radius: 8px;
                            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                        }
                        h1 {
                            margin-bottom: 1rem;
                            color: #333;
                        }
                        form {
                            display: flex;
                            flex-direction: column;
                        }
                        input[type="password"] {
                            padding: 0.5rem;
                            margin-bottom: 1rem;
                            border: 1px solid #ccc;
                            border-radius: 4px;
                        }
                        input[type="submit"] {
                            padding: 0.5rem;
                            background-color: #007BFF;
                            color: #fff;
                            border: none;
                            border-radius: 4px;
                            cursor: pointer;
                        }
                        input[type="submit"]:hover {
                            background-color: #0056b3;
                        }
                    """.trimIndent())
                        }
                    }
                }
                body {
                    div(classes = "container") {
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
        }

        post("/reset-password") {
            val params = call.receiveParameters()
            val token = params["token"] ?: return@post call.respondText("Missing token", status = HttpStatusCode.BadRequest)
            val newPassword = params["newPassword"] ?: return@post call.respondText("Missing new password", status = HttpStatusCode.BadRequest)

            // Проверяем валидность нового пароля
            if (!isValidPassword(newPassword)) {
                call.respondHtml {
                    head {
                        title { +"Invalid Password" }
                        style {
                            unsafe {
                                raw(
                                    """
                            body {
                                background-color: #f4f4f4;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                font-family: Arial, sans-serif;
                                margin: 0;
                            }
                            .container {
                                background-color: #fff;
                                padding: 2rem;
                                border-radius: 8px;
                                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                                text-align: center;
                            }
                            .error {
                                color: red;
                                font-weight: bold;
                                margin-bottom: 1rem;
                            }
                            button {
                                padding: 0.5rem 1rem;
                                background-color: #007BFF;
                                color: #fff;
                                border: none;
                                border-radius: 4px;
                                cursor: pointer;
                            }
                            button:hover {
                                background-color: #0056b3;
                            }
                            """.trimIndent()
                                )
                            }
                        }
                    }
                    body {
                        div(classes = "container") {
                            h1 { +"Invalid Password" }
                            p(classes = "error") {
                                +"Password must be at least 6 characters and contain both letters and digits"
                            }
                            // Кнопка, которая при нажатии возвращает пользователя на предыдущую страницу
                            button(type = ButtonType.button) {
                                attributes["onclick"] = "history.back()"
                                +"Go Back"
                            }
                        }
                    }
                }
                return@post
            }

            // Найдите пользователя по reset-токену
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
                call.respondHtml {
                    head {
                        title { +"Password Reset Successful" }
                        style {
                            unsafe {
                                raw(
                                    """
                            body {
                                background-color: #f4f4f4;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                font-family: Arial, sans-serif;
                                margin: 0;
                            }
                            .container {
                                background-color: #fff;
                                padding: 2rem;
                                border-radius: 8px;
                                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                                text-align: center;
                            }
                            h1 {
                                color: #333;
                                margin-bottom: 1rem;
                            }
                            p {
                                color: #555;
                            }
                            a {
                                display: inline-block;
                                margin-top: 1rem;
                                color: #007BFF;
                                text-decoration: none;
                            }
                            a:hover {
                                text-decoration: underline;
                            }
                            """.trimIndent()
                                )
                            }
                        }
                    }
                    body {
                        div(classes = "container") {
                            h1 { +"Password Reset Successful" }
                            p { +"Your password has been reset successfully. You can now log in with your new password." }
                        }
                    }
                }
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

            if (userService.verifyUser(user.id!!)) {
                call.respondHtml {
                    head {
                        title { +"Email Confirmed" }
                        style {
                            unsafe {
                                raw(
                                    """
                            body {
                                background-color: #f0f0f0;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                margin: 0;
                                font-family: Arial, sans-serif;
                            }
                            .container {
                                background-color: #fff;
                                padding: 2rem;
                                border-radius: 8px;
                                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                                text-align: center;
                            }
                            h1 {
                                color: #333;
                                margin-bottom: 1rem;
                            }
                            p {
                                color: #555;
                            }
                            """
                                )
                            }
                        }
                    }
                    body {
                        div("container") {
                            h1 { +"Email confirmed !" }
                            p { +"Your email has been successfully verified. You can now log in." }
                        }
                    }
                }
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
