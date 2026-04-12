package com.spring.redis.sample.service.impl

import com.spring.redis.sample.repository.StoreLikeRepository
import com.spring.redis.sample.service.StoreLikeService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * Write-Behind 패턴으로 좋아요 관리
 *
 * 흐름:
 *   like/unlike → Redis Set 즉시 반영 + dirty Set에 storeId 추가
 *   StoreLikeSyncScheduler → 1분마다 dirty 매장을 Redis → DB 동기화
 *
 * Redis 장애 시:
 *   getLikeCount / isLiked → Redis 키 없으면 DB 폴백
 *   서버 재시작 시 → StoreLikeRestoreRunner가 DB → Redis 복원
 *
 * Key 구조:
 *   store:likes:{storeId}  → Set (좋아요한 username 집합)
 *   store:likes:dirty      → Set (DB 동기화가 필요한 storeId 집합)
 */
@Service
class StoreLikeServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val storeLikeRepository: StoreLikeRepository
) : StoreLikeService {

    companion object {
        const val DIRTY_KEY = "store:likes:dirty"
    }

    /**
     * 좋아요 - Set SADD
     *
     * Redis 저장 형태:
     *   Key:   store:likes:1
     *   Value: { "user1", "user2", "user3" }
     *
     * SADD 반환값:
     *   1: 새로 추가됨 (좋아요 성공) → dirty 마킹
     *   0: 이미 존재함 (중복 좋아요) → dirty 마킹 불필요
     */
    override fun like(storeId: Long, username: String): Boolean {
        val added = redisTemplate.opsForSet().add(likeKey(storeId), username)
        if (added == 1L) {
            // DB 동기화가 필요한 storeId를 dirty Set에 추가
            redisTemplate.opsForSet().add(DIRTY_KEY, storeId.toString())
        }
        return added == 1L
    }

    /**
     * 좋아요 취소 - Set SREM
     * 실제로 제거된 경우(1L)에만 dirty 마킹
     */
    override fun unlike(storeId: Long, username: String) {
        val removed = redisTemplate.opsForSet().remove(likeKey(storeId), username)
        if (removed == 1L) {
            redisTemplate.opsForSet().add(DIRTY_KEY, storeId.toString())
        }
    }

    /**
     * 좋아요 수 조회 - Set SCARD
     *
     * Redis 우선 조회:
     *   - Redis 키 존재 → SCARD (O(1), 인메모리)
     *   - Redis 키 없음 → DB COUNT 폴백 (서버 재시작 후 복원 전까지)
     */
    override fun getLikeCount(storeId: Long): Long {
        val key = likeKey(storeId)
        if (redisTemplate.hasKey(key)) {
            return redisTemplate.opsForSet().size(key) ?: 0L
        }
        // Redis 키 없음 → DB 폴백
        return storeLikeRepository.countByStoreId(storeId)
    }

    /**
     * 좋아요 여부 확인 - Set SISMEMBER
     *
     * Redis 우선 조회:
     *   - Redis 키 존재 → SISMEMBER (O(1))
     *   - Redis 키 없음 → DB EXISTS 폴백
     */
    override fun isLiked(storeId: Long, username: String): Boolean {
        val key = likeKey(storeId)
        if (redisTemplate.hasKey(key)) {
            return redisTemplate.opsForSet().isMember(key, username) ?: false
        }
        // Redis 키 없음 → DB 폴백
        return storeLikeRepository.existsByStoreIdAndUsername(storeId, username)
    }

    private fun likeKey(storeId: Long) = "store:likes:$storeId"
}
