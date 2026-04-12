package com.spring.redis.sample.controller

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.service.StoreGeoService
import com.spring.redis.sample.service.StoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/stores")
class StoreController(
    private val storeService: StoreService,
    private val storeGeoService: StoreGeoService
) {

    /**
     * DB 기반 반경 내 매장 검색 (GIS - ST_Distance_Sphere)
     * GET /api/stores/nearby?lat=37.4979&lng=127.0276&radius=500
     *
     * - 정확한 거리 계산
     * - 매장 수가 많으면 DB 풀스캔 발생 가능
     */
    @GetMapping("/nearby")
    fun getNearbyStores(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "500") radius: Double
    ): ResponseEntity<List<StoreResponse>> =
        ResponseEntity.ok(storeService.findNearbyStores(lat, lng, radius))

    /**
     * Redis Geo 기반 반경 내 매장 검색 (GEOSEARCH)
     * GET /api/stores/nearby/geo?lat=37.4979&lng=127.0276&radius=500
     *
     * - DB 부하 없이 Redis 인메모리에서 거리 계산
     * - 앱 시작 시 전체 매장이 Redis Geo에 적재되어 있어야 함 (StoreGeoInitializer)
     */
    @GetMapping("/nearby/geo")
    fun getNearbyStoresViaGeo(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "500") radius: Double
    ): ResponseEntity<List<StoreResponse>> =
        ResponseEntity.ok(storeGeoService.findNearbyStores(lat, lng, radius))
}
