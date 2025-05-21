package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaceDto(
    val id: Int,
    val name: String,
    val rating: Float,
    val description: String
)
