package com.example.data.datasource.remote.model.request

import kotlinx.serialization.Serializable

@Serializable
data class AddCartItemRequest(
    val productId: Int,
    val quantity: Int,
)
