package com.spring.redis.sample.initializer

import com.spring.redis.sample.repository.StoreLikeRepository
import com.spring.redis.sample.repository.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

/**
 * 서버 재시작 시 DB → Redis 좋아요 데이터 복원
 *
 * Write-Behind 패턴의 약점:
 *   Redis가 재시작되면 Set 데이터가 모두 사라짐
 *   → getLikeCount/isLiked가 DB 폴백으로 동작하지만 Redis 이점이 없어짐
 *   → 이 Runner가 시작 시점에 DB 데이터를 Redis로 미리 적재해 캐시 워밍업
 *
 * SADD는 멱등성: 이미 존재하는 값 추가 시 무시되므로 재시작해도 안전
 */
@Component
class StoreLikeRestoreRunner(
    private val storeRepository: StoreRepository,
    private val storeLikeRepository: StoreLikeRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val stores = storeRepository.findAll()
        if (stores.isEmpty()) return

        log.info("[StoreLikeRestore] DB → Redis 좋아요 복원 시작: {}개 매장", stores.size)

        var totalLikes = 0
        for (store in stores) {
            val likers = storeLikeRepository.findAllByStoreId(store.id)
            if (likers.isEmpty()) continue

            val likeKey = "store:likes:${store.id}"

            // Redis 키가 이미 존재하면 복원 불필요 (Redis 재시작이 아닌 앱만 재시작)
            if (redisTemplate.hasKey(likeKey)) continue

            val usernames = likers.map { it.username }.toTypedArray()
            redisTemplate.opsForSet().add(likeKey, *usernames)

            totalLikes += likers.size
        }

        log.info("[StoreLikeRestore] 복원 완료: {}건", totalLikes)
    }
}
