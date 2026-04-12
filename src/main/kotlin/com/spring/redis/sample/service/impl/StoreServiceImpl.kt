package com.spring.redis.sample.service.impl

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.redis.CacheStampedeProtector
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.StoreService
import com.spring.redis.sample.service.StoreService.Companion.DEFAULT_RADIUS
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StoreServiceImpl(
    private val storeRepository: StoreRepository,
    private val stampedeProtector: CacheStampedeProtector
) : StoreService {

    /**
     * 반경 내 매장 목록 조회 - Cache Stampede 방지 적용
     *
     * @Cacheable 대신 CacheStampedeProtector를 직접 사용하는 이유:
     *   @Cacheable은 캐시 Miss 시 여러 스레드가 동시에 DB를 조회하는 Cache Stampede 문제가 있음
     *   CacheStampedeProtector는 분산 락으로 단 하나의 스레드만 DB를 조회하도록 보장
     *
     * 캐시 설정 (RedisConfig.cacheManager):
     *   - 캐시 이름: "nearby-stores" → TTL 10분 적용
     *   - 캐시 키:   "lat:lng:radius" 형태
     *
     * 참고: 실무에서는 좌표를 격자 단위로 반올림(소수점 3자리 ≈ 111m)해 캐시 히트율을 높인다.
     */
    override fun findNearbyStores(lat: Double, lng: Double, radius: Double): List<StoreResponse> {
        val cacheKey = "$lat:$lng:$radius"

        return stampedeProtector.get(
            cacheName = "nearby-stores",
            cacheKey  = cacheKey
        ) {
            storeRepository.findStoresWithinRadius(lat, lng, radius)
                .map { StoreResponse.from(it) }
        }
    }
}
