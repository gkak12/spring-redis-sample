package com.spring.redis.sample.service.impl

import com.spring.redis.sample.service.StoreLikeService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class StoreLikeServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>
) : StoreLikeService {

    /**
     * 매장 좋아요 - Set SADD
     *
     * Redis 저장 형태:
     *   Key:   store:likes:1
     *   Value: { "user1", "user2", "user3" }  ← 중복 없는 집합
     *
     * SADD 반환값:
     *   1: 새로 추가됨 (좋아요 성공)
     *   0: 이미 존재함 (중복 좋아요 시도)
     *
     * DB 방식과의 차이:
     *   DB: SELECT → 중복 확인 → INSERT (2번 쿼리 또는 unique 제약 에러 처리)
     *   Set: SADD 1번으로 중복 방지 + 추가 동시 처리
     */
    override fun like(storeId: Long, username: String): Boolean {
        val added = redisTemplate.opsForSet().add(likeKey(storeId), username)
        return added == 1L
    }

    /**
     * 매장 좋아요 취소 - Set SREM
     * 존재하지 않는 값을 제거해도 에러 없이 무시됨
     */
    override fun unlike(storeId: Long, username: String) {
        redisTemplate.opsForSet().remove(likeKey(storeId), username)
    }

    /**
     * 좋아요 수 조회 - Set SCARD
     * O(1) 시간복잡도로 전체 카운트 반환
     */
    override fun getLikeCount(storeId: Long): Long {
        return redisTemplate.opsForSet().size(likeKey(storeId)) ?: 0L
    }

    /**
     * 좋아요 여부 확인 - Set SISMEMBER
     * O(1) 시간복잡도
     *
     * DB 방식과의 차이:
     *   DB: SELECT count(*) WHERE store_id = ? AND user_id = ?
     *   Set: SISMEMBER → 인메모리 O(1) 조회
     */
    override fun isLiked(storeId: Long, username: String): Boolean {
        return redisTemplate.opsForSet().isMember(likeKey(storeId), username) ?: false
    }

    private fun likeKey(storeId: Long) = "store:likes:$storeId"
}
