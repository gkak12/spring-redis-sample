package com.spring.redis.sample.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("RateLimiter - Sliding Window")
class RateLimiterTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun setUp() {
        rateLimiter = RateLimiter(redisTemplate)
    }

    @Test
    @DisplayName("요청 수가 한도 미만이면 허용한다")
    fun `should allow request when under limit`() {
        // Lua 스크립트 실행 결과: 현재 요청 포함 카운트 반환 (양수 = 허용)
        whenever(redisTemplate.execute(any(), any<List<String>>(), anyVararg()))
            .thenReturn(3L)

        val result = rateLimiter.isAllowed(
            identifier    = "192.168.0.1",
            endpoint      = "POST:/api/auth/login",
            limit         = 5,
            windowSeconds = 60
        )

        assertThat(result.allowed).isTrue()
        assertThat(result.current).isEqualTo(3)
        assertThat(result.remaining).isEqualTo(2)  // limit(5) - current(3)
        assertThat(result.limit).isEqualTo(5)
        assertThat(result.windowSeconds).isEqualTo(60)
    }

    @Test
    @DisplayName("요청 수가 한도에 도달하면 차단한다")
    fun `should reject request when limit reached`() {
        // Lua 스크립트 결과: -1 = 한도 초과
        whenever(redisTemplate.execute(any(), any<List<String>>(), anyVararg()))
            .thenReturn(-1L)

        val result = rateLimiter.isAllowed(
            identifier    = "192.168.0.1",
            endpoint      = "POST:/api/auth/login",
            limit         = 5,
            windowSeconds = 60
        )

        assertThat(result.allowed).isFalse()
        assertThat(result.remaining).isEqualTo(0)
    }

    @Test
    @DisplayName("Redis 응답이 null이면 허용으로 처리한다 (안전 기본값)")
    fun `should allow request when redis returns null`() {
        whenever(redisTemplate.execute(any(), any<List<String>>(), anyVararg()))
            .thenReturn(null)

        val result = rateLimiter.isAllowed(
            identifier    = "192.168.0.1",
            endpoint      = "GET:/api/search",
            limit         = 30,
            windowSeconds = 60
        )

        // null → 0L로 처리 → 0 >= 0 이므로 allowed = true
        assertThat(result.allowed).isTrue()
    }

    @Test
    @DisplayName("요청이 딱 한도와 같을 때 차단한다")
    fun `should reject when current equals limit`() {
        whenever(redisTemplate.execute(any(), any<List<String>>(), anyVararg()))
            .thenReturn(-1L)

        val result = rateLimiter.isAllowed("ip", "endpoint", limit = 5, windowSeconds = 60)

        assertThat(result.allowed).isFalse()
        assertThat(result.current).isEqualTo(5)
    }

    @Test
    @DisplayName("remaining은 0 미만으로 내려가지 않는다")
    fun `remaining should not go below zero`() {
        whenever(redisTemplate.execute(any(), any<List<String>>(), anyVararg()))
            .thenReturn(-1L)

        val result = rateLimiter.isAllowed("ip", "endpoint", limit = 5, windowSeconds = 60)

        assertThat(result.remaining).isGreaterThanOrEqualTo(0)
    }
}
