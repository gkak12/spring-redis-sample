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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

@ExtendWith(MockitoExtension::class)
@DisplayName("CacheStampedeProtector - Cache Stampede 방지")
class CacheStampedeProtectorTest {

    @Mock private lateinit var cacheManager: CacheManager
    @Mock private lateinit var lockManager: RedisLockManager
    @Mock private lateinit var cache: Cache
    @Mock private lateinit var cacheValueWrapper: Cache.ValueWrapper

    private lateinit var protector: CacheStampedeProtector

    companion object {
        private const val CACHE_NAME = "nearby-stores"
        private const val CACHE_KEY  = "37.5:127.0:500.0"
    }

    @BeforeEach
    fun setUp() {
        protector = CacheStampedeProtector(cacheManager, lockManager)
        whenever(cacheManager.getCache(CACHE_NAME)).thenReturn(cache)
    }

    @Test
    @DisplayName("캐시 Hit 시 loader를 실행하지 않고 즉시 반환한다")
    fun `should return cached value without calling loader on cache hit`() {
        val cachedValue = listOf("store1", "store2")
        whenever(cache.get(CACHE_KEY)).thenReturn(cacheValueWrapper)
        whenever(cacheValueWrapper.get()).thenReturn(cachedValue)

        var loaderCalled = false
        val result = protector.get(CACHE_NAME, CACHE_KEY) {
            loaderCalled = true
            cachedValue
        }

        assertThat(result).isEqualTo(cachedValue)
        assertThat(loaderCalled).isFalse()
        // 캐시 Hit이므로 락 획득 시도조차 하지 않음
        verify(lockManager, never()).acquire(any())
    }

    @Test
    @DisplayName("캐시 Miss + 락 획득 성공 시 loader를 실행하고 캐시에 저장한다")
    fun `should call loader and cache result when lock acquired on cache miss`() {
        val freshValue = listOf("store1", "store2")
        // 1차 캐시 확인: Miss
        // 2차 캐시 확인 (Double-Checked Locking): Miss
        whenever(cache.get(CACHE_KEY)).thenReturn(null)

        val lockKey = "stampede:lock:$CACHE_NAME:$CACHE_KEY"
        whenever(lockManager.acquire(lockKey)).thenReturn("lock-value-ulid")

        val result = protector.get(CACHE_NAME, CACHE_KEY) { freshValue }

        assertThat(result).isEqualTo(freshValue)
        // 캐시에 저장되었는지 확인
        verify(cache).put(eq(CACHE_KEY), eq(freshValue))
        // 락 해제 확인
        verify(lockManager).release(eq(lockKey), eq("lock-value-ulid"))
    }

    @Test
    @DisplayName("락 획득 후 Double-Checked Locking에서 캐시 Hit 시 loader를 실행하지 않는다")
    fun `should not call loader when double-check hits cache after lock acquired`() {
        val cachedValue = listOf("store-by-other-thread")

        // 1차 캐시 확인: Miss → 락 획득 시도
        // 2차 캐시 확인 (Double-Checked Locking): Hit → 다른 스레드가 이미 처리
        whenever(cache.get(CACHE_KEY))
            .thenReturn(null)                // 1차: Miss
            .thenReturn(cacheValueWrapper)   // 2차: Hit
        whenever(cacheValueWrapper.get()).thenReturn(cachedValue)

        val lockKey = "stampede:lock:$CACHE_NAME:$CACHE_KEY"
        whenever(lockManager.acquire(lockKey)).thenReturn("lock-value-ulid")

        var loaderCalled = false
        val result = protector.get(CACHE_NAME, CACHE_KEY) {
            loaderCalled = true
            emptyList<String>()
        }

        assertThat(result).isEqualTo(cachedValue)
        assertThat(loaderCalled).isFalse()  // loader 실행 안 됨
        verify(cache, never()).put(any(), any())
        verify(lockManager).release(eq(lockKey), eq("lock-value-ulid"))
    }

    @Test
    @DisplayName("loader 실행 중 예외 발생 시에도 락이 반드시 해제된다")
    fun `should always release lock even when loader throws exception`() {
        whenever(cache.get(CACHE_KEY)).thenReturn(null)

        val lockKey = "stampede:lock:$CACHE_NAME:$CACHE_KEY"
        whenever(lockManager.acquire(lockKey)).thenReturn("lock-value-ulid")

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) {
            protector.get(CACHE_NAME, CACHE_KEY) {
                throw RuntimeException("DB 연결 실패")
            }
        }

        // finally 블록에서 락 해제 확인
        verify(lockManager).release(eq(lockKey), eq("lock-value-ulid"))
    }

    @Test
    @DisplayName("등록되지 않은 캐시 이름 사용 시 예외를 발생시킨다")
    fun `should throw when cache name not registered`() {
        whenever(cacheManager.getCache("unknown-cache")).thenReturn(null)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            protector.get("unknown-cache", CACHE_KEY) { "value" }
        }
    }
}
