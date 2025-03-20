package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val is_verified: Boolean
)