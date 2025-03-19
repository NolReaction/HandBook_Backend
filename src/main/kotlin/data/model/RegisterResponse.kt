package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val token: String,
    val userId: Int,
    val userEmail: String,
    val is_verified: Boolean
)