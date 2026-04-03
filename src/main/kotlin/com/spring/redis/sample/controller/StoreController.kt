package com.spring.redis.sample.controller

import com.spring.redis.sample.dto.store.StoreResponse
import com.spring.redis.sample.service.StoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/stores")
class StoreController(
    private val storeService: StoreService
) {

    /**
     * 반경 내 편의점 목록 조회 (GIS - ST_Distance_Sphere)
     * GET /api/stores/nearby?lat=37.4979&lng=127.0276&radius=500
     *
     * @param lat    현재 위도
     * @param lng    현재 경도
     * @param radius 검색 반경 (미터, 기본값: 500)
     */
    @GetMapping("/nearby")
    fun getNearbyStores(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "500") radius: Double
    ): ResponseEntity<List<StoreResponse>> =
        ResponseEntity.ok(storeService.findNearbyStores(lat, lng, radius))
}
