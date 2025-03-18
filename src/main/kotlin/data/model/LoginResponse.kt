package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val userId: Int,
    val userEmail: String
)
