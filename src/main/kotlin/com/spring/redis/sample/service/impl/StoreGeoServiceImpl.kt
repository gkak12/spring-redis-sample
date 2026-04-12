package com.spring.redis.sample.service.impl

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.entity.Store
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.StoreGeoService
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Point
import org.springframework.data.redis.domain.geo.Metrics
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.domain.geo.GeoReference
import org.springframework.data.redis.domain.geo.GeoShape
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class StoreGeoServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val storeRepository: StoreRepository
) : StoreGeoService {

    companion object {
        // 모든 매장 좌표를 저장하는 Redis Geo 키
        private const val GEO_KEY = "stores:geo"

        // 한 번에 반환할 최대 매장 수
        private const val MAX_RESULTS = 100L
    }

    /**
     * 매장 좌표를 Redis Geo에 등록
     *
     * Redis GEOADD 명령: GEOADD stores:geo {경도} {위도} {storeId}
     * Point 순서 주의: Redis Geo는 (경도, 위도) 순서 — 위도·경도와 반대
     */
    override fun addStore(store: Store) {
        redisTemplate.opsForGeo()
            .add(GEO_KEY, Point(store.longitude, store.latitude), store.id.toString())
    }

    /**
     * Redis GEOSEARCH로 반경 내 매장 검색
     *
     * 처리 흐름:
     * 1. Redis Geo에서 반경 내 storeId 목록 + 거리 조회
     * 2. storeId로 DB에서 매장 상세 정보 일괄 조회 (IN 쿼리 1번)
     * 3. Redis 결과 순서(거리 오름차순) 유지하며 StoreResponse 조합
     *
     * DB 방식(ST_Distance_Sphere)과의 차이:
     * - DB 방식: 매 요청마다 전체 레코드 풀스캔 + 거리 계산
     * - Geo 방식: Redis 인메모리에서 거리 계산 → DB는 ID 기반 단순 조회만
     */
    override fun findNearbyStores(lat: Double, lng: Double, radiusMeters: Double): List<StoreResponse> {
        val results = redisTemplate.opsForGeo().search(
            GEO_KEY,
            // 검색 기준점: Point(경도, 위도) 순서
            GeoReference.fromCoordinate(Point(lng, lat)),
            // 검색 범위: 반경 N미터
            GeoShape.byRadius(Distance(radiusMeters, Metrics.METERS)),
            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance() // 각 매장까지의 거리 포함
                .sortAscending()   // 가까운 순 정렬
                .limit(MAX_RESULTS)
        ) ?: return emptyList()

        // Redis에서 받은 storeId 목록으로 DB 일괄 조회 (N+1 방지)
        val storeIds = results.content.map { it.content.name.toString().toLong() }
        val storeMap = storeRepository.findAllById(storeIds).associateBy { it.id }

        // Redis 결과 순서(거리 오름차순)를 유지하며 응답 객체 조합
        return results.content.mapNotNull { geoResult ->
            val storeId = geoResult.content.name.toString().toLong()
            val store = storeMap[storeId] ?: return@mapNotNull null

            StoreResponse(
                id = store.id,
                name = store.name,
                address = store.address,
                latitude = store.latitude,
                longitude = store.longitude,
                category = store.category.name,
                phone = store.phone,
                // Metrics.METERS로 검색했으므로 distance.value는 미터 단위
                distanceMeters = geoResult.distance.value.roundToInt()
            )
        }
    }
}
