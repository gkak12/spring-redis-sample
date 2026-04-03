package com.spring.redis.sample.service

import com.spring.redis.sample.dto.store.StoreResponse

interface StoreService {
    fun findNearbyStores(lat: Double, lng: Double, radius: Double = DEFAULT_RADIUS): List<StoreResponse>

    companion object {
        const val DEFAULT_RADIUS = 500.0
    }
}
