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

    /**
     * 유저별 최근 검색어 저장
     * POST /api/search/recent/{username}
     * Body: { "keyword": "치킨" }
     */
    @PostMapping("/recent/{username}")
    fun saveRecent(
        @PathVariable username: String,
        @Valid @RequestBody request: SearchRequest
    ): ResponseEntity<Map<String, String>> {
        searchService.saveRecentKeyword(username, request.keyword)
        return ResponseEntity.ok(mapOf("message" to "최근 검색어가 저장되었습니다.", "keyword" to request.keyword))
    }

    /**
     * 유저별 최근 검색어 조회 (최신순, 최대 10개)
     * GET /api/search/recent/{username}
     */
    @GetMapping("/recent/{username}")
    fun getRecent(@PathVariable username: String): ResponseEntity<List<String>> =
        ResponseEntity.ok(searchService.getRecentKeywords(username))

    /**
     * 유저별 최근 검색어 전체 삭제
     * DELETE /api/search/recent/{username}
     */
    @DeleteMapping("/recent/{username}")
    fun clearRecent(@PathVariable username: String): ResponseEntity<Map<String, String>> {
        searchService.clearRecentKeywords(username)
        return ResponseEntity.ok(mapOf("message" to "최근 검색어가 삭제되었습니다."))
    }
}
