package com.spring.redis.sample.service.impl

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.StoreService
import com.spring.redis.sample.service.StoreService.Companion.DEFAULT_RADIUS
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StoreServiceImpl(
    private val storeRepository: StoreRepository
) : StoreService {

    /**
     * 반경 내 매장 목록 조회 - GIS 쿼리 결과를 10분간 캐싱
     *
     * 캐시 키: "lat:lng:radius"
     * 참고: 실무에서는 좌표를 격자 단위로 반올림(3자리 ≈ 111m)해 캐시 히트율을 높인다.
     */
    @Cacheable(value = ["nearby-stores"], key = "#lat + ':' + #lng + ':' + #radius")
    override fun findNearbyStores(lat: Double, lng: Double, radius: Double): List<StoreResponse> =
        storeRepository.findStoresWithinRadius(lat, lng, radius)
            .map { StoreResponse.from(it) }
}
