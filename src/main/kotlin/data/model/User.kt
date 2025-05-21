package com.example.data.model

import kotlinx.serialization.Serializable

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