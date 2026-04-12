package com.spring.redis.sample.scheduler

import com.spring.redis.sample.entity.StoreLike
import com.spring.redis.sample.repository.StoreLikeRepository
import com.spring.redis.sample.service.impl.StoreLikeServiceImpl.Companion.DIRTY_KEY
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Write-Behind 패턴: Redis → DB 주기적 동기화
 *
 * 동작 방식:
 * 1. StoreLikeServiceImpl.like/unlike 호출 시 store:likes:dirty에 storeId 추가
 * 2. 이 스케줄러가 1분마다 dirty Set을 확인
 * 3. 변경된 매장에 대해 Redis Set(현재 상태) ↔ DB(이전 상태)를 diff 후 반영
 * 4. 동기화 완료된 storeId는 dirty Set에서 제거
 *
 * 장점:
 * - like/unlike 요청이 폭발적으로 들어와도 DB는 1분에 1번만 갱신
 * - 동일 매장에 100번 좋아요/취소가 반복되어도 DB 부하는 1회로 처리
 *
 * 주의:
 * - Redis 장애 시 dirty Set도 유실 → 최대 1분치 변경 사항 손실 가능
 * - 허용 가능한 유실 범위라면 이 패턴이 적합, 엄격한 일관성이 필요하면 Write-Through 사용
 */
@Component
class StoreLikeSyncScheduler(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val storeLikeRepository: StoreLikeRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 1분마다 dirty 매장의 좋아요 데이터를 DB에 동기화
     *
     * fixedDelay: 이전 실행 완료 후 1분 대기 (동시 실행 방지)
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun syncToDatabase() {
        val dirtyStoreIds = redisTemplate.opsForSet().members(DIRTY_KEY)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: return

        if (dirtyStoreIds.isEmpty()) return

        log.info("[StoreLikeSync] 동기화 시작: {}개 매장", dirtyStoreIds.size)

        for (storeId in dirtyStoreIds) {
            try {
                syncStore(storeId)
                // 동기화 성공한 storeId만 dirty에서 제거 (실패 시 다음 주기에 재시도)
                redisTemplate.opsForSet().remove(DIRTY_KEY, storeId.toString())
            } catch (e: Exception) {
                log.error("[StoreLikeSync] storeId={} 동기화 실패, 다음 주기에 재시도: {}", storeId, e.message)
            }
        }

        log.info("[StoreLikeSync] 동기화 완료")
    }

    /**
     * 단일 매장의 좋아요 상태를 Redis → DB에 반영
     *
     * diff 방식:
     *   - Redis에 있지만 DB에 없는 username → INSERT
     *   - DB에 있지만 Redis에 없는 username → DELETE
     */
    private fun syncStore(storeId: Long) {
        val likeKey = "store:likes:$storeId"

        // Redis 현재 상태 (좋아요한 username 집합)
        val redisLikers: Set<String> = redisTemplate.opsForSet()
            .members(likeKey)
            ?.map { it.toString() }
            ?.toSet()
            ?: emptySet()

        // DB 현재 상태
        val dbLikers: Set<String> = storeLikeRepository
            .findAllByStoreId(storeId)
            .map { it.username }
            .toSet()

        // Redis에는 있지만 DB에 없는 것 → 좋아요 추가
        val toInsert = redisLikers - dbLikers
        // DB에는 있지만 Redis에 없는 것 → 좋아요 취소
        val toDelete = dbLikers - redisLikers

        if (toInsert.isNotEmpty()) {
            val newLikes = toInsert.map { username -> StoreLike(storeId = storeId, username = username) }
            storeLikeRepository.saveAll(newLikes)
        }

        if (toDelete.isNotEmpty()) {
            storeLikeRepository.deleteAllByStoreIdAndUsernameIn(storeId, toDelete)
        }

        log.debug("[StoreLikeSync] storeId={}: +{}건 추가, -{}건 삭제", storeId, toInsert.size, toDelete.size)
    }
}
