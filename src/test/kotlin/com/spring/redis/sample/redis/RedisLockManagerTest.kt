package com.spring.redis.sample.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
@DisplayName("RedisLockManager - 분산 락")
class RedisLockManagerTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Mock
    private lateinit var valueOps: ValueOperations<String, Any>

    private lateinit var lockManager: RedisLockManager

    companion object {
        private const val LOCK_KEY = "test:lock:key"
    }

    @BeforeEach
    fun setUp() {
        lockManager = RedisLockManager(redisTemplate)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
    }

    @Test
    @DisplayName("SETNX 성공 시 ULID lockValue를 반환한다")
    fun `should return lockValue when lock acquired`() {
        whenever(valueOps.setIfAbsent(eq(LOCK_KEY), any<String>(), any<Duration>()))
            .thenReturn(true)

        val lockValue = lockManager.acquire(LOCK_KEY)

        assertThat(lockValue).isNotNull()
        assertThat(lockValue).isNotBlank()
    }

    @Test
    @DisplayName("SETNX 실패 시 null을 반환한다 (이미 락 존재)")
    fun `should return null when lock already held`() {
        whenever(valueOps.setIfAbsent(eq(LOCK_KEY), any<String>(), any<Duration>()))
            .thenReturn(false)

        val lockValue = lockManager.acquire(LOCK_KEY)

        assertThat(lockValue).isNull()
    }

    @Test
    @DisplayName("락 획득 시 매번 고유한 lockValue를 발급한다")
    fun `should generate unique lockValue each time`() {
        whenever(valueOps.setIfAbsent(any(), any<String>(), any<Duration>()))
            .thenReturn(true)

        val value1 = lockManager.acquire("lock:1")
        val value2 = lockManager.acquire("lock:2")

        assertThat(value1).isNotEqualTo(value2)
    }

    @Test
    @DisplayName("Lua 스크립트로 락 해제 성공 시 정상 처리된다")
    fun `should release lock successfully`() {
        whenever(redisTemplate.execute(any(), any<List<String>>(), any<String>()))
            .thenReturn(1L)

        // 예외 없이 정상 완료되어야 함
        lockManager.release(LOCK_KEY, "some-lock-value")

        verify(redisTemplate).execute(any(), eq(listOf(LOCK_KEY)), eq("some-lock-value"))
    }

    @Test
    @DisplayName("Lua 스크립트가 0을 반환하면 경고만 출력하고 예외는 발생하지 않는다")
    fun `should not throw when release returns 0`() {
        // 이미 만료되었거나 다른 스레드가 소유한 락을 해제 시도하는 경우
        whenever(redisTemplate.execute(any(), any<List<String>>(), any<String>()))
            .thenReturn(0L)

        // 예외 없이 정상 완료 (경고 로그만 출력)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            lockManager.release(LOCK_KEY, "expired-lock-value")
        }
    }
}
