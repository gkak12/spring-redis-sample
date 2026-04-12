package com.spring.redis.sample.service.impl

import com.github.f4b6a3.ulid.UlidCreator
import com.spring.redis.sample.dto.search.TrendingKeyword
import com.spring.redis.sample.service.SearchService
import com.spring.redis.sample.service.SearchService.Companion.TOP_N
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class SearchServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>
) : SearchService {

    /**
     * 검색어 기록 - 새 검색어가 들어올 때마다 트렌딩 캐시 무효화
     */
    @CacheEvict(value = ["trending-keywords"], allEntries = true)
    override fun recordSearch(keyword: String) {
        val bucketKey = getCurrentBucketKey()
        redisTemplate.opsForZSet().incrementScore(bucketKey, keyword, 1.0)
        redisTemplate.expire(bucketKey, BUCKET_TTL_HOURS, TimeUnit.HOURS)
    }

    /**
     * 실시간 인기 검색어 조회 - ZSet unionAndStore 결과를 1분간 캐싱
     *
     * 캐시 키: topN 값 (기본 10)
     * recordSearch 호출 시 @CacheEvict로 자동 무효화
     */
    @Cacheable(value = ["trending-keywords"], key = "#topN")
    override fun getTrendingKeywords(topN: Long): List<TrendingKeyword> {
        val bucketKeys = getRecentBucketKeys()
        val resultKey = "trending:result:${UlidCreator.getUlid()}"

        return try {
            redisTemplate.opsForZSet()
                .unionAndStore(bucketKeys.first(), bucketKeys.drop(1), resultKey)

            val result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(resultKey, 0, topN - 1)

            result?.mapIndexed { index, tuple ->
                TrendingKeyword(
                    rank = index + 1,
                    keyword = tuple.value?.toString() ?: "",
                    score = tuple.score?.toLong() ?: 0L
                )
            } ?: emptyList()
        } finally {
            redisTemplate.delete(resultKey)
        }
    }

    private fun getCurrentBucketKey(): String {
        return "$BUCKET_PREFIX${LocalDateTime.now().format(BUCKET_FORMATTER)}"
    }

    private fun getRecentBucketKeys(): List<String> {
        val now = LocalDateTime.now()
        return (0 until BUCKET_COUNT).map { hoursAgo ->
            "$BUCKET_PREFIX${now.minusHours(hoursAgo.toLong()).format(BUCKET_FORMATTER)}"
        }
    }

    companion object {
        private const val BUCKET_PREFIX = "search:bucket:"
        private const val BUCKET_TTL_HOURS = 25L
        private const val BUCKET_COUNT = 24
        private val BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}
