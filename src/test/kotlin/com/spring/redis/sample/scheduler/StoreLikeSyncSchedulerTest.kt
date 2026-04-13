package com.spring.redis.sample.scheduler

import com.spring.redis.sample.entity.StoreLike
import com.spring.redis.sample.repository.StoreLikeRepository
import com.spring.redis.sample.service.impl.StoreLikeServiceImpl.Companion.DIRTY_KEY
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations

@ExtendWith(MockitoExtension::class)
@DisplayName("StoreLikeSyncScheduler - Write-Behind DB 동기화")
class StoreLikeSyncSchedulerTest {

    @Mock private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Mock private lateinit var setOps: SetOperations<String, Any>
    @Mock private lateinit var storeLikeRepository: StoreLikeRepository

    private lateinit var scheduler: StoreLikeSyncScheduler

    @BeforeEach
    fun setUp() {
        scheduler = StoreLikeSyncScheduler(redisTemplate, storeLikeRepository)
        whenever(redisTemplate.opsForSet()).thenReturn(setOps)
    }

    @Test
    @DisplayName("dirty Set이 비어있으면 DB 동기화를 수행하지 않는다")
    fun `should skip sync when dirty set is empty`() {
        whenever(setOps.members(DIRTY_KEY)).thenReturn(emptySet())

        scheduler.syncToDatabase()

        verify(storeLikeRepository, never()).findAllByStoreId(any())
    }

    @Test
    @DisplayName("dirty Set이 null이면 DB 동기화를 수행하지 않는다")
    fun `should skip sync when dirty set is null`() {
        whenever(setOps.members(DIRTY_KEY)).thenReturn(null)

        scheduler.syncToDatabase()

        verify(storeLikeRepository, never()).findAllByStoreId(any())
    }

    @Nested
    @DisplayName("syncStore() - Redis → DB diff 동기화")
    inner class SyncStore {

        @Test
        @DisplayName("Redis에 있지만 DB에 없는 좋아요를 INSERT한다")
        fun `should insert likes that exist in Redis but not in DB`() {
            val storeId = 1L
            whenever(setOps.members(DIRTY_KEY)).thenReturn(setOf(storeId.toString()))
            // Redis: user1, user2 좋아요 중
            whenever(setOps.members("store:likes:$storeId"))
                .thenReturn(setOf("user1", "user2"))
            // DB: 아무도 좋아요 안 함
            whenever(storeLikeRepository.findAllByStoreId(storeId)).thenReturn(emptyList())

            scheduler.syncToDatabase()

            // user1, user2 INSERT 확인
            val captor = argumentCaptor<List<StoreLike>>()
            verify(storeLikeRepository).saveAll(captor.capture())
            val savedUsernames = captor.firstValue.map { it.username }.toSet()
            org.assertj.core.api.Assertions.assertThat(savedUsernames)
                .containsExactlyInAnyOrder("user1", "user2")
        }

        @Test
        @DisplayName("DB에 있지만 Redis에 없는 좋아요를 DELETE한다")
        fun `should delete likes that exist in DB but not in Redis`() {
            val storeId = 2L
            whenever(setOps.members(DIRTY_KEY)).thenReturn(setOf(storeId.toString()))
            // Redis: 아무도 좋아요 안 함 (좋아요 취소됨)
            whenever(setOps.members("store:likes:$storeId")).thenReturn(emptySet())
            // DB: user1이 이전에 좋아요 했었음
            whenever(storeLikeRepository.findAllByStoreId(storeId))
                .thenReturn(listOf(StoreLike(storeId = storeId, username = "user1")))

            scheduler.syncToDatabase()

            verify(storeLikeRepository).deleteAllByStoreIdAndUsernameIn(
                eq(storeId), eq(setOf("user1"))
            )
        }

        @Test
        @DisplayName("Redis와 DB가 동일하면 INSERT/DELETE를 수행하지 않는다")
        fun `should not insert or delete when Redis and DB are in sync`() {
            val storeId = 3L
            whenever(setOps.members(DIRTY_KEY)).thenReturn(setOf(storeId.toString()))
            whenever(setOps.members("store:likes:$storeId")).thenReturn(setOf("user1"))
            whenever(storeLikeRepository.findAllByStoreId(storeId))
                .thenReturn(listOf(StoreLike(storeId = storeId, username = "user1")))

            scheduler.syncToDatabase()

            verify(storeLikeRepository, never()).saveAll(any<List<StoreLike>>())
            verify(storeLikeRepository, never()).deleteAllByStoreIdAndUsernameIn(any(), any())
        }

        @Test
        @DisplayName("동기화 성공 시 dirty Set에서 storeId를 제거한다")
        fun `should remove storeId from dirty set after successful sync`() {
            val storeId = 4L
            whenever(setOps.members(DIRTY_KEY)).thenReturn(setOf(storeId.toString()))
            whenever(setOps.members("store:likes:$storeId")).thenReturn(emptySet())
            whenever(storeLikeRepository.findAllByStoreId(storeId)).thenReturn(emptyList())

            scheduler.syncToDatabase()

            verify(setOps).remove(eq(DIRTY_KEY), eq(storeId.toString()))
        }

        @Test
        @DisplayName("동기화 실패 시 dirty Set에서 storeId를 제거하지 않는다 (다음 주기 재시도)")
        fun `should not remove storeId from dirty set when sync fails`() {
            val storeId = 5L
            whenever(setOps.members(DIRTY_KEY)).thenReturn(setOf(storeId.toString()))
            whenever(setOps.members("store:likes:$storeId")).thenReturn(setOf("user1"))
            // DB 조회 시 예외 발생
            whenever(storeLikeRepository.findAllByStoreId(storeId))
                .thenThrow(RuntimeException("DB 연결 실패"))

            // 예외가 외부로 전파되지 않아야 함 (다른 매장 동기화 계속 진행)
            org.junit.jupiter.api.Assertions.assertDoesNotThrow {
                scheduler.syncToDatabase()
            }

            // dirty Set에서 제거되지 않음 → 다음 주기에 재시도
            verify(setOps, never()).remove(eq(DIRTY_KEY), eq(storeId.toString()))
        }
    }
}
