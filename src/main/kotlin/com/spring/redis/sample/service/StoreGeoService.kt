package com.spring.redis.sample.service

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.entity.Store

interface StoreGeoService {

    /**
     * 매장 1개를 Redis Geo에 등록
     * 앱 시작 시 StoreGeoInitializer가 전체 매장을 일괄 등록하며,
     * 이후 신규 매장 추가 시 개별 호출
     */
    fun addStore(store: Store)

    /**
     * Redis Geo 기반 반경 내 매장 검색
     *
     * DB의 ST_Distance_Sphere 대신 Redis GEOSEARCH 명령 사용
     * → DB 부하 없이 인메모리에서 거리 계산
     *
     * @param lat          현재 위도
     * @param lng          현재 경도
     * @param radiusMeters 검색 반경 (미터)
     */
    fun findNearbyStores(lat: Double, lng: Double, radiusMeters: Double): List<StoreResponse>
}
