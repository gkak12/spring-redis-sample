package com.spring.redis.sample.controller

import com.spring.redis.sample.dto.search.SearchRequest
import com.spring.redis.sample.dto.search.TrendingKeyword
import com.spring.redis.sample.service.SearchService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService
) {

    /**
     * 실시간 인기 검색어 Top 10 조회
     * GET /api/search/trending
     */
    @GetMapping("/trending")
    fun getTrending(): ResponseEntity<List<TrendingKeyword>> =
        ResponseEntity.ok(searchService.getTrendingKeywords())

    /**
     * 검색어 기록 (검색 시 호출)
     * POST /api/search
     * Body: { "keyword": "편의점" }
     */
    @PostMapping
    fun search(@Valid @RequestBody request: SearchRequest): ResponseEntity<Map<String, String>> {
        searchService.recordSearch(request.keyword)
        return ResponseEntity.ok(mapOf("message" to "검색어가 기록되었습니다.", "keyword" to request.keyword))
    }
}
