package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUsernameRequest(val username: String)