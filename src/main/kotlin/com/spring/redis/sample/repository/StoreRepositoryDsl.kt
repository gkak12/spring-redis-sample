package com.spring.redis.sample.repository

import com.spring.redis.sample.dto.store.StoreWithDistance

interface StoreRepositoryDsl {
    fun findStoresWithinRadius(lat: Double, lng: Double, radius: Double): List<StoreWithDistance>
}
