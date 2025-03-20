package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordRequest(val email: String)