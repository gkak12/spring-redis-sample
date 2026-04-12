package com.spring.redis.sample.redis

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

/**
 * Cache Stampede(캐시 스탬피드) 방지 컴포넌트
 *
 * 문제 상황:
 *   캐시 TTL 만료 순간 수백 건의 요청이 동시에 캐시 Miss
 *   → 모두 DB로 직행 → DB 순간 부하 폭증
 *
 * 해결 방식 (Mutex Lock):
 *   1. 캐시 확인 → Hit: 즉시 반환
 *   2. Miss → 분산 락 획득 시도
 *   3. 락 획득 성공 → 락 재확인(Double-Checked Locking) → DB 조회 → 캐시 저장 → 락 해제
 *   4. 락 획득 실패 (다른 스레드가 이미 DB 조회 중) → 일정 간격으로 재시도 → 캐시 반환
 *   5. 대기 타임아웃 → 직접 DB 조회 (안전장치)
 *
 *  Double-Checked Locking (3단계):
 *   락 획득 후 캐시를 다시 확인하는 이유:
 *   락을 기다리는 동안 다른 스레드가 이미 캐시를 채웠을 수 있기 때문
 *   → 불필요한 DB 중복 조회 방지
 */
@Component
class CacheStampedeProtector(
    private val cacheManager: CacheManager,
    private val lockManager: RedisLockManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LOCK_PREFIX    = "stampede:lock"
        private const val WAIT_INTERVAL  = 100L  // 락 대기 폴링 간격 (ms)
        private const val MAX_WAIT_MS    = 3_000L // 최대 대기 시간 (ms) - 락 TTL(3s)과 맞춤
    }

    /**
     * Cache Stampede 보호가 적용된 캐시 조회
     *
     * @param cacheName  RedisCacheManager에 등록된 캐시 이름 (TTL 설정이 적용됨)
     * @param cacheKey   캐시 내 고유 키
     * @param loader     캐시 Miss 시 실행할 DB 조회 로직
     */
    fun <T> get(
        cacheName: String,
        cacheKey: String,
        loader: () -> T
    ): T {
        val cache  = cacheManager.getCache(cacheName)
            ?: throw IllegalArgumentException("등록되지 않은 캐시: $cacheName")
        val lockKey = "$LOCK_PREFIX:$cacheName:$cacheKey"

        // 1단계: 캐시 확인 (Fast Path)
        cache.get(cacheKey)?.get()?.let {
            @Suppress("UNCHECKED_CAST")
            return it as T
        }

        // 2단계: 분산 락 획득 시도
        val lockValue = lockManager.acquire(lockKey)

        return if (lockValue != null) {
            try {
                // 3단계: Double-Checked Locking
                // 락 대기 중 다른 스레드가 이미 캐시를 채웠을 수 있으므로 재확인
                cache.get(cacheKey)?.get()?.let {
                    log.debug("[Stampede] 락 획득 후 캐시 Hit (다른 스레드가 먼저 처리): key={}", cacheKey)
                    @Suppress("UNCHECKED_CAST")
                    return it as T
                }

                // 4단계: DB 조회 후 캐시 저장
                log.debug("[Stampede] DB 조회 후 캐시 저장: cacheName={}, key={}", cacheName, cacheKey)
                val value = loader()
                cache.put(cacheKey, value)
                value
            } finally {
                // 성공/실패 무관하게 락 반드시 해제
                lockManager.release(lockKey, lockValue)
            }
        } else {
            // 5단계: 락 획득 실패 → 다른 스레드가 DB 조회 완료할 때까지 대기
            waitAndGet(cacheName, cacheKey, cache, loader)
        }
    }

    /**
     * 락 획득 실패 시 캐시가 채워질 때까지 폴링 대기
     * 최대 대기 시간(3초) 초과 시 직접 DB 조회로 폴백 (안전장치)
     */
    private fun <T> waitAndGet(
        cacheName: String,
        cacheKey: String,
        cache: org.springframework.cache.Cache,
        fallback: () -> T
    ): T {
        var waited = 0L

        while (waited < MAX_WAIT_MS) {
            Thread.sleep(WAIT_INTERVAL)
            waited += WAIT_INTERVAL

            cache.get(cacheKey)?.get()?.let {
                log.debug("[Stampede] 대기 후 캐시 Hit: key={}, waited={}ms", cacheKey, waited)
                @Suppress("UNCHECKED_CAST")
                return it as T
            }
        }

        // 타임아웃: 캐시가 끝내 채워지지 않은 경우 직접 DB 조회
        log.warn("[Stampede] 대기 타임아웃({}ms), DB 직접 조회: cacheName={}, key={}", MAX_WAIT_MS, cacheName, cacheKey)
        return fallback()
    }
}
