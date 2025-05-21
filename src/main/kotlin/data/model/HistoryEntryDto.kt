package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntryDto(
    val place: PlaceDto,
    val createdAt: String  // ISO-строка, например
)