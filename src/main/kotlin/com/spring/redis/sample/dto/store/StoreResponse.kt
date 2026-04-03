package com.spring.redis.sample.dto.store

import kotlin.math.roundToInt

data class StoreResponse(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val phone: String?,
    val distanceMeters: Int
) {
    companion object {
        fun from(store: StoreWithDistance): StoreResponse = StoreResponse(
            id = store.id,
            name = store.name,
            address = store.address,
            latitude = store.latitude,
            longitude = store.longitude,
            category = store.category,
            phone = store.phone,
            distanceMeters = store.distance.roundToInt()
        )
    }
}
