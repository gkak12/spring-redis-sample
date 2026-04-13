package com.spring.redis.sample.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import com.github.f4b6a3.ulid.UlidCreator
import java.time.Duration

/**
 * Redis 기반 분산 락 매니저
 *
 * - 락 획득: SETNX로 ULID 값 저장 (TTL 포함)
 * - 락 해제: Lua 스크립트로 소유 여부 확인 후 원자적 삭제
 *   → 내가 획득한 락만 해제 가능, 타 스레드 락을 실수로 삭제하는 문제 방지
 */
@Component
class RedisLockManager(
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val LOCK_TTL = Duration.ofSeconds(3)

        /**
         * 락 해제 Lua 스크립트
         * 저장된 값이 ARGV[1](lockValue)과 일치할 때만 삭제 → 원자적 비교-삭제
         */
        private val RELEASE_SCRIPT = DefaultRedisScript<Long>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
            Long::class.java
        )
    }

    /**
     * 락 획득 시도
     * @return 락 획득 성공 시 lockValue(ULID), 실패 시 null
     */
    fun acquire(lockKey: String): String? {
        val lockValue = UlidCreator.getUlid().toString()
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, LOCK_TTL) ?: false
        return if (acquired) lockValue else null
    }

    /**
     * 락 해제
     * lockValue가 일치해야만 삭제 → 락 TTL 만료 후 다른 스레드가 재획득한 락을 건드리지 않음
     */
    fun release(lockKey: String, lockValue: String) {
        val deleted = redisTemplate.execute(RELEASE_SCRIPT, listOf(lockKey), lockValue)
        if (deleted == 0L) {
            log.warn("락 해제 실패 - 이미 만료되었거나 다른 스레드가 소유 중: key={}", lockKey)
        }
    }
}
