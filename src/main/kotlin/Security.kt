package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val jwtAudience = "handbook-app"
    // val jwtDomain = "https://jwt-provider-domain/"
    val jwtRealm = "HandbookRealm"
    val jwtSecret = environmentValue("HANDBOOK_JWT_SECRET")
    install(Authentication) {
        jwt("jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
                // JWTPrincipal(credential.payload) // Тест
            }
        }
    }
}
