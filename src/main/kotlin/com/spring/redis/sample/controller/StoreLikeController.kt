package com.spring.redis.sample.controller

import com.spring.redis.sample.ratelimit.RateLimit
import com.spring.redis.sample.service.StoreLikeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stores/{storeId}/likes")
class StoreLikeController(
    private val storeLikeService: StoreLikeService
) {

    /**
     * 매장 좋아요
     * POST /api/stores/{storeId}/likes?username=user1
     * Rate Limit: 1분에 20회 (도배 방지)
     */
    @RateLimit(limit = 20, windowSeconds = 60)
    @PostMapping
    fun like(
        @PathVariable storeId: Long,
        @RequestParam username: String
    ): ResponseEntity<Map<String, Any>> {
        val success = storeLikeService.like(storeId, username)
        val message = if (success) "좋아요가 추가되었습니다." else "이미 좋아요한 매장입니다."
        return ResponseEntity.ok(mapOf("message" to message, "liked" to success))
    }

    /**
     * 매장 좋아요 취소
     * DELETE /api/stores/{storeId}/likes?username=user1
     */
    @DeleteMapping
    fun unlike(
        @PathVariable storeId: Long,
        @RequestParam username: String
    ): ResponseEntity<Map<String, String>> {
        storeLikeService.unlike(storeId, username)
        return ResponseEntity.ok(mapOf("message" to "좋아요가 취소되었습니다."))
    }

    /**
     * 매장 좋아요 수 조회
     * GET /api/stores/{storeId}/likes/count
     */
    @GetMapping("/count")
    fun getLikeCount(@PathVariable storeId: Long): ResponseEntity<Map<String, Long>> =
        ResponseEntity.ok(mapOf("likeCount" to storeLikeService.getLikeCount(storeId)))

    /**
     * 특정 유저의 좋아요 여부 확인
     * GET /api/stores/{storeId}/likes/check?username=user1
     */
    @GetMapping("/check")
    fun isLiked(
        @PathVariable storeId: Long,
        @RequestParam username: String
    ): ResponseEntity<Map<String, Boolean>> =
        ResponseEntity.ok(mapOf("liked" to storeLikeService.isLiked(storeId, username)))
}
