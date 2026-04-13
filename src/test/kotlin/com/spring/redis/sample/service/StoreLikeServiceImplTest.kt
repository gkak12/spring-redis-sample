package com.spring.redis.sample.service

import com.spring.redis.sample.entity.StoreLike
import com.spring.redis.sample.repository.StoreLikeRepository
import com.spring.redis.sample.service.impl.StoreLikeServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations

@ExtendWith(MockitoExtension::class)
@DisplayName("StoreLikeServiceImpl - 좋아요 Write-Behind")
class StoreLikeServiceImplTest {

    @Mock private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Mock private lateinit var setOps: SetOperations<String, Any>
    @Mock private lateinit var storeLikeRepository: StoreLikeRepository

    private lateinit var service: StoreLikeServiceImpl

    companion object {
        private const val STORE_ID = 1L
        private const val USERNAME = "testUser"
        private val LIKE_KEY = "store:likes:$STORE_ID"
    }

    @BeforeEach
    fun setUp() {
        service = StoreLikeServiceImpl(redisTemplate, storeLikeRepository)
        whenever(redisTemplate.opsForSet()).thenReturn(setOps)
    }

    @Nested
    @DisplayName("like()")
    inner class Like {

        @Test
        @DisplayName("새 좋아요 추가 시 true 반환 및 dirty 마킹")
        fun `should return true and mark dirty when new like added`() {
            whenever(setOps.add(eq(LIKE_KEY), eq(USERNAME))).thenReturn(1L)

            val result = service.like(STORE_ID, USERNAME)

            assertThat(result).isTrue()
            // dirty Set에 storeId 추가 확인
            verify(setOps).add(eq(StoreLikeServiceImpl.DIRTY_KEY), eq(STORE_ID.toString()))
        }

        @Test
        @DisplayName("이미 좋아요한 경우 false 반환 및 dirty 마킹 안 함")
        fun `should return false and not mark dirty on duplicate like`() {
            whenever(setOps.add(eq(LIKE_KEY), eq(USERNAME))).thenReturn(0L)

            val result = service.like(STORE_ID, USERNAME)

            assertThat(result).isFalse()
            verify(setOps, never()).add(eq(StoreLikeServiceImpl.DIRTY_KEY), any())
        }
    }

    @Nested
    @DisplayName("unlike()")
    inner class Unlike {

        @Test
        @DisplayName("좋아요 취소 성공 시 dirty 마킹")
        fun `should mark dirty when unlike succeeds`() {
            whenever(setOps.remove(eq(LIKE_KEY), eq(USERNAME))).thenReturn(1L)

            service.unlike(STORE_ID, USERNAME)

            verify(setOps).add(eq(StoreLikeServiceImpl.DIRTY_KEY), eq(STORE_ID.toString()))
        }

        @Test
        @DisplayName("존재하지 않는 좋아요 취소 시 dirty 마킹 안 함")
        fun `should not mark dirty when unlike target does not exist`() {
            whenever(setOps.remove(eq(LIKE_KEY), eq(USERNAME))).thenReturn(0L)

            service.unlike(STORE_ID, USERNAME)

            verify(setOps, never()).add(eq(StoreLikeServiceImpl.DIRTY_KEY), any())
        }
    }

    @Nested
    @DisplayName("getLikeCount()")
    inner class GetLikeCount {

        @Test
        @DisplayName("Redis 키 존재 시 SCARD 결과를 반환한다")
        fun `should return Redis SCARD when key exists`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(true)
            whenever(setOps.size(LIKE_KEY)).thenReturn(42L)

            val count = service.getLikeCount(STORE_ID)

            assertThat(count).isEqualTo(42L)
            verify(storeLikeRepository, never()).countByStoreId(any())
        }

        @Test
        @DisplayName("Redis 키 없으면 DB COUNT로 폴백한다")
        fun `should fallback to DB count when Redis key missing`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(false)
            whenever(storeLikeRepository.countByStoreId(STORE_ID)).thenReturn(15L)

            val count = service.getLikeCount(STORE_ID)

            assertThat(count).isEqualTo(15L)
            verify(setOps, never()).size(any())
        }

        @Test
        @DisplayName("Redis size()가 null 반환 시 0을 반환한다")
        fun `should return 0 when Redis size returns null`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(true)
            whenever(setOps.size(LIKE_KEY)).thenReturn(null)

            val count = service.getLikeCount(STORE_ID)

            assertThat(count).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("isLiked()")
    inner class IsLiked {

        @Test
        @DisplayName("Redis 키 존재 시 SISMEMBER 결과를 반환한다")
        fun `should return Redis SISMEMBER when key exists`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(true)
            whenever(setOps.isMember(LIKE_KEY, USERNAME)).thenReturn(true)

            val result = service.isLiked(STORE_ID, USERNAME)

            assertThat(result).isTrue()
            verify(storeLikeRepository, never()).existsByStoreIdAndUsername(any(), any())
        }

        @Test
        @DisplayName("Redis 키 없으면 DB EXISTS로 폴백한다")
        fun `should fallback to DB exists when Redis key missing`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(false)
            whenever(storeLikeRepository.existsByStoreIdAndUsername(STORE_ID, USERNAME)).thenReturn(true)

            val result = service.isLiked(STORE_ID, USERNAME)

            assertThat(result).isTrue()
            verify(setOps, never()).isMember(any(), any())
        }

        @Test
        @DisplayName("Redis isMember()가 null 반환 시 false를 반환한다")
        fun `should return false when Redis isMember returns null`() {
            whenever(redisTemplate.hasKey(LIKE_KEY)).thenReturn(true)
            whenever(setOps.isMember(LIKE_KEY, USERNAME)).thenReturn(null)

            val result = service.isLiked(STORE_ID, USERNAME)

            assertThat(result).isFalse()
        }
    }
}
