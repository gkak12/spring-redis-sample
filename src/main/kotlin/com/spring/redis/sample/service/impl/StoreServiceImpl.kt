package com.spring.redis.sample.service.impl

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.StoreService
import com.spring.redis.sample.service.StoreService.Companion.DEFAULT_RADIUS
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StoreServiceImpl(
    private val storeRepository: StoreRepository
) : StoreService {

    override fun findNearbyStores(lat: Double, lng: Double, radius: Double): List<StoreResponse> =
        storeRepository.findStoresWithinRadius(lat, lng, radius)
            .map { StoreResponse.from(it) }
}
