package com.spring.redis.sample.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.ulid.UlidCreator
import com.spring.redis.sample.dto.search.TrendingKeyword
import com.spring.redis.sample.service.SearchService
import com.spring.redis.sample.service.SearchService.Companion.MAX_RECENT
import com.spring.redis.sample.service.SearchService.Companion.TOP_N
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class SearchServiceImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
    objectMapper: ObjectMapper
) : SearchService {

    // Pipeline에서 직접 바이트 직렬화 시 redisTemplate과 동일한 직렬화 방식 유지
    private val keySerializer = StringRedisSerializer()
    private val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

    /**
     * 검색어 기록 - Pipeline으로 incrementScore + expire 2개 명령을 1번 왕복으로 처리
     *
     * Pipeline 미사용 시: Redis 왕복 2회 (incrementScore → expire)
     * Pipeline 사용 시:  Redis 왕복 1회 (두 명령을 묶어서 전송)
     *
     * @CacheEvict: 새 검색어 유입 시 트렌딩 캐시 무효화
     */
    @CacheEvict(value = ["trending-keywords"], allEntries = true)
    override fun recordSearch(keyword: String) {
        val bucketKey = getCurrentBucketKey()
        val rawKey = keySerializer.serialize(bucketKey)!!
        val rawMember = valueSerializer.serialize(keyword)!!

        redisTemplate.executePipelined { connection ->
            // incrementScore: ZSet에서 keyword 점수 1 증가 (없으면 1로 생성)
            connection.zSetCommands().zIncrBy(rawKey, 1.0, rawMember)
            // expire: 버킷 TTL 갱신 (매 검색마다 만료 시간 연장)
            connection.keyCommands().expire(rawKey, BUCKET_TTL_HOURS * 3600L)
            null // executePipelined의 RedisCallback은 반드시 null 반환
        }
    }

    /**
     * 실시간 인기 검색어 조회 - ZSet unionAndStore 결과를 1분간 캐싱
     *
     * 캐시 키 예시: "SearchService:getTrendingKeywords:10"
     * recordSearch 호출 시 @CacheEvict로 자동 무효화
     */
    @Cacheable(value = ["trending-keywords"], keyGenerator = "customKeyGenerator")
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

    /**
     * 유저별 최근 검색어 저장
     *
     * LPUSH: 리스트 앞에 추가 (최신순 유지)
     * LTRIM: 최대 MAX_RECENT개만 유지 (오래된 항목 자동 제거)
     *
     * Redis 저장 형태:
     *   Key:   recent:search:user1
     *   Value: ["치킨", "피자", "카페", ...]  ← 왼쪽이 최신
     */
    override fun saveRecentKeyword(username: String, keyword: String) {
        val key = recentKey(username)
        redisTemplate.opsForList().leftPush(key, keyword)
        // 0 ~ MAX_RECENT-1 인덱스만 유지 → 초과분 자동 삭제
        redisTemplate.opsForList().trim(key, 0, MAX_RECENT - 1)
    }

    /**
     * 유저별 최근 검색어 조회
     * LRANGE 0 -1: 리스트 전체 반환 (최신순)
     */
    override fun getRecentKeywords(username: String): List<String> {
        return redisTemplate.opsForList()
            .range(recentKey(username), 0, -1)
            ?.filterNotNull()
            ?.map { it.toString() }
            ?: emptyList()
    }

    /**
     * 유저별 최근 검색어 전체 삭제
     */
    override fun clearRecentKeywords(username: String) {
        redisTemplate.delete(recentKey(username))
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

    private fun recentKey(username: String) = "recent:search:$username"

    companion object {
        private const val BUCKET_PREFIX = "search:bucket:"
        private const val BUCKET_TTL_HOURS = 25L
        private const val BUCKET_COUNT = 24
        private val BUCKET_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}
