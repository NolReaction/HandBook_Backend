package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Полная модель пользователя, возвращаемая в ответе на регистрацию/авторизацию.
 * Содержит все поля, включая хеш пароля (но возвращать его наружу не нужно),
 * аватар (ключ), username и код для верификации.
 */

@Serializable
data class User(
    val id: Int? = null,
    val email: String,
    val password: String,
    val username: String = "",
    val avatar: String,
    val is_verified: Boolean,
    val verification_code: String? = null
)