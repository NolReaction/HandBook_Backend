package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Лёгкая DTO-модель, которую мы возвращаем клиенту
 * при получении профиля или обновлении username/avatar.
 */

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val username: String = "",
    val avatar: String,
    val is_verified: Boolean
)