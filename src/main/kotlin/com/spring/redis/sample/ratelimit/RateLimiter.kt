package com.spring.redis.sample.ratelimit

import com.github.f4b6a3.ulid.UlidCreator
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Redis 기반 Sliding Window Rate Limiter
 *
 * Fixed Window와의 차이:
 *   Fixed Window: 윈도우 경계에서 2배 요청 허용 문제 발생
 *     예) 59초에 60회 + 61초에 60회 → 2초 사이에 120회 허용
 *
 *   Sliding Window: 현재 시점 기준으로 과거 N초를 항상 계산 → 경계 문제 없음
 *     예) 어느 시점이든 과거 60초 내 요청이 60회를 넘으면 차단
 *
 * 구현 방식 (ZSet):
 *   Key:   rate:limit:{identifier}:{endpoint}
 *   Score: 요청 시각 (ms timestamp)
 *   Value: ULID (요청별 고유 ID)
 *
 *   ZREMRANGEBYSCORE → 윈도우 밖 항목 제거
 *   ZCARD           → 현재 윈도우 내 요청 수 확인
 *   ZADD            → 현재 요청 추가
 *
 * Lua 스크립트로 원자적 처리:
 *   확인 → 추가를 원자적으로 실행하지 않으면 동시 요청 시 limit을 초과할 수 있음
 */
@Component
class RateLimiter(
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        /**
         * Sliding Window Lua 스크립트
         *
         * KEYS[1]: rate limit 키 (rate:limit:{identifier}:{endpoint})
         * ARGV[1]: 현재 시각 (ms)
         * ARGV[2]: 윈도우 크기 (ms)
         * ARGV[3]: 허용 요청 수
         * ARGV[4]: 현재 요청 ID (ULID)
         *
         * 반환값:
         *   양수: 현재 윈도우 내 누적 요청 수 (허용)
         *     -1: 요청 한도 초과 (차단)
         */
        private val SLIDING_WINDOW_SCRIPT = DefaultRedisScript<Long>(
            """
            local key      = KEYS[1]
            local now      = tonumber(ARGV[1])
            local window   = tonumber(ARGV[2])
            local limit    = tonumber(ARGV[3])
            local reqId    = ARGV[4]

            -- 윈도우 밖의 오래된 요청 제거 (현재시각 - 윈도우 범위 이전 항목 삭제)
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

            -- 현재 윈도우 내 요청 수 확인
            local count = redis.call('ZCARD', key)

            if count < limit then
                -- 현재 요청 추가 (score: 현재시각, value: 고유 요청 ID)
                redis.call('ZADD', key, now, reqId)
                -- TTL을 윈도우 크기로 설정 (자동 만료)
                redis.call('PEXPIRE', key, window)
                return count + 1
            else
                return -1
            end
            """.trimIndent(),
            Long::class.java
        )
    }

    /**
     * 요청 허용 여부 판단
     *
     * @param identifier    요청자 식별자 (IP 주소 또는 username)
     * @param endpoint      엔드포인트 식별자 (HTTP method + URI)
     * @param limit         허용 요청 수
     * @param windowSeconds 시간 윈도우 (초)
     * @return RateLimitResult (허용 여부 + 현재 요청 수 + 남은 요청 수)
     */
    fun isAllowed(
        identifier: String,
        endpoint: String,
        limit: Int,
        windowSeconds: Long
    ): RateLimitResult {
        val key        = "rate:limit:$identifier:$endpoint"
        val now        = System.currentTimeMillis()
        val windowMs   = windowSeconds * 1_000L
        val requestId  = UlidCreator.getUlid().toString()

        val count = redisTemplate.execute(
            SLIDING_WINDOW_SCRIPT,
            listOf(key),
            now.toString(),
            windowMs.toString(),
            limit.toString(),
            requestId
        ) ?: 0L

        return if (count >= 0) {
            RateLimitResult(allowed = true,  current = count.toInt(), limit = limit, windowSeconds = windowSeconds)
        } else {
            RateLimitResult(allowed = false, current = limit,         limit = limit, windowSeconds = windowSeconds)
        }
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val current: Int,   // 현재 윈도우 내 요청 수
    val limit: Int,     // 최대 허용 요청 수
    val windowSeconds: Long,
    val remaining: Int = (limit - current).coerceAtLeast(0)  // 남은 요청 수
)
