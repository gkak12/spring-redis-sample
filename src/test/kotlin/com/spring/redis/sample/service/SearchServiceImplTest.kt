package com.spring.redis.sample.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.spring.redis.sample.service.SearchService.Companion.MAX_RECENT
import com.spring.redis.sample.service.impl.SearchServiceImpl
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("SearchServiceImpl - 최근 검색어 (List)")
class SearchServiceImplTest {

    @Mock private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Mock private lateinit var listOps: ListOperations<String, Any>

    private lateinit var service: SearchServiceImpl

    companion object {
        private const val USERNAME = "user1"
        private val RECENT_KEY = "recent:search:$USERNAME"
    }

    @BeforeEach
    fun setUp() {
        service = SearchServiceImpl(redisTemplate, ObjectMapper())
        whenever(redisTemplate.opsForList()).thenReturn(listOps)
    }

    @Nested
    @DisplayName("saveRecentKeyword()")
    inner class SaveRecentKeyword {

        @Test
        @DisplayName("LPUSH로 리스트 앞에 추가하고 LTRIM으로 최대 10개 유지한다")
        fun `should leftPush keyword and trim to MAX_RECENT`() {
            service.saveRecentKeyword(USERNAME, "치킨")

            // LPUSH: 최신 검색어를 리스트 앞에 추가
            verify(listOps).leftPush(eq(RECENT_KEY), eq("치킨"))
            // LTRIM: 0 ~ MAX_RECENT-1 인덱스만 유지
            verify(listOps).trim(eq(RECENT_KEY), eq(0L), eq(MAX_RECENT - 1))
        }

        @Test
        @DisplayName("여러 키워드 저장 시 각각 LPUSH + LTRIM이 호출된다")
        fun `should call leftPush and trim for each keyword`() {
            service.saveRecentKeyword(USERNAME, "피자")
            service.saveRecentKeyword(USERNAME, "파스타")

            verify(listOps).leftPush(eq(RECENT_KEY), eq("피자"))
            verify(listOps).leftPush(eq(RECENT_KEY), eq("파스타"))
        }
    }

    @Nested
    @DisplayName("getRecentKeywords()")
    inner class GetRecentKeywords {

        @Test
        @DisplayName("LRANGE로 전체 리스트를 반환한다 (최신순)")
        fun `should return all keywords from LRANGE`() {
            val keywords = listOf<Any>("치킨", "피자", "카페")
            whenever(listOps.range(eq(RECENT_KEY), eq(0L), eq(-1L))).thenReturn(keywords)

            val result = service.getRecentKeywords(USERNAME)

            assertThat(result).containsExactly("치킨", "피자", "카페")
        }

        @Test
        @DisplayName("Redis가 null을 반환하면 빈 리스트를 반환한다")
        fun `should return empty list when Redis returns null`() {
            whenever(listOps.range(any(), any(), any())).thenReturn(null)

            val result = service.getRecentKeywords(USERNAME)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("리스트가 비어있으면 빈 리스트를 반환한다")
        fun `should return empty list when no recent keywords`() {
            whenever(listOps.range(any(), any(), any())).thenReturn(emptyList())

            val result = service.getRecentKeywords(USERNAME)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("clearRecentKeywords()")
    inner class ClearRecentKeywords {

        @Test
        @DisplayName("해당 유저의 최근 검색어 키를 삭제한다")
        fun `should delete recent keyword key for user`() {
            service.clearRecentKeywords(USERNAME)

            verify(redisTemplate).delete(eq(RECENT_KEY))
        }

        @Test
        @DisplayName("다른 유저 키에는 영향을 주지 않는다")
        fun `should only delete key for the specified user`() {
            service.clearRecentKeywords("anotherUser")

            verify(redisTemplate).delete(eq("recent:search:anotherUser"))
            // user1 키는 삭제 안 됨
            org.mockito.kotlin.verifyNoMoreInteractions(redisTemplate)
        }
    }
}
