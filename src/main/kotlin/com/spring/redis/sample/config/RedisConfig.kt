package com.spring.redis.sample.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig {

    /**
     * String/객체 모두 처리하는 단일 RedisTemplate
     * - 키: StringRedisSerializer (사람이 읽기 쉬운 형태)
     * - 값: Jackson2JsonRedisSerializer (@class 없는 순수 JSON)
     */
    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val serializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = serializer
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = serializer
        return template
    }

    /**
     * @Cacheable 등 Spring Cache 추상화에서 사용하는 CacheManager
     *
     * 캐시별 TTL:
     * - nearby-stores    : 10분 (매장 정보는 자주 바뀌지 않음)
     * - trending-keywords:  1분 (검색어 트렌드는 빠르게 갱신)
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisCacheManager {
        val serializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration(
                "nearby-stores",
                defaultConfig.entryTtl(Duration.ofMinutes(10))
            )
            .withCacheConfiguration(
                "trending-keywords",
                defaultConfig.entryTtl(Duration.ofMinutes(1))
            )
            .build()
    }
}
