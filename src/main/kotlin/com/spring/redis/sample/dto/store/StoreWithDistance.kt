package com.spring.redis.sample.dto.store

data class StoreWithDistance(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val phone: String?,
    val distance: Double
)
