package com.spring.redis.sample.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.KeyGenerator
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
@EnableCaching // @Cacheable, @CacheEvict 등 Spring Cache 애노테이션 활성화
class RedisConfig {

    /**
     * 커스텀 캐시 키 생성기
     *
     * 생성 형식: "{클래스명}:{메서드명}:{파라미터1}:{파라미터2}:..."
     * 예시:
     *   - StoreServiceImpl.findNearbyStores(37.5, 127.0, 500.0)
     *     → "StoreService:findNearbyStores:37.5:127.0:500.0"
     *   - SearchServiceImpl.getTrendingKeywords(10)
     *     → "SearchService:getTrendingKeywords:10"
     *
     * 파라미터가 없으면 "no-args"를 사용해 키 충돌 방지
     *
     * spring-cache-sample과의 차이:
     *   - 첫 번째 파라미터만 키에 포함하던 방식을 개선해 모든 파라미터를 포함
     *   - 클래스명에서 "Impl" 접미사를 제거해 인터페이스 이름 기준으로 통일
     */
    @Bean
    fun customKeyGenerator(): KeyGenerator = KeyGenerator { target, method, params ->
        // Spring CGLIB 프록시가 붙이는 "$$EnhancerBySpringCGLIB$$..." 제거
        val className = target.javaClass.simpleName
            .split("$$")[0]       // 프록시 접미사 제거
            .removeSuffix("Impl") // "Impl" 접미사 제거 → 인터페이스 이름 기준으로 통일

        val paramKey = if (params.isEmpty()) "no-args" else params.joinToString(":")

        "$className:${method.name}:$paramKey"
    }

    /**
     * String/객체 모두 처리하는 단일 RedisTemplate
     *
     * redisTemplate.opsForValue().set("key", someObject) 처럼
     * 직접 Redis를 조작할 때 사용 (AuthService의 토큰 저장, SearchService의 ZSet 등)
     *
     * 키: StringRedisSerializer  → "refresh:token:user1" 형태로 저장 (사람이 읽기 쉬움)
     * 값: Jackson2JsonRedisSerializer → {"name":"홍길동"} 형태의 순수 JSON 저장
     *     GenericJackson2JsonRedisSerializer와 달리 @class 타입 정보를 저장하지 않아
     *     클래스 경로가 바뀌어도 기존 캐시 데이터를 역직렬화할 수 있음
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
     * @Cacheable, @CacheEvict 등 Spring Cache 추상화에서 사용하는 CacheManager
     *
     * redisTemplate과 별도로 존재하는 이유:
     * - redisTemplate: 개발자가 Redis 명령을 직접 실행하는 저수준 API
     * - cacheManager:  @Cacheable 애노테이션이 내부적으로 사용하는 고수준 추상화
     *
     * 캐시별 TTL:
     * - nearby-stores    : 10분 (매장 위치 정보는 자주 바뀌지 않음)
     * - trending-keywords:  1분 (검색어 트렌드는 빠르게 갱신)
     * - 그 외 캐시       :  TTL 없음 (defaultConfig 적용)
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisCacheManager {
        // redisTemplate과 동일한 직렬화 방식 사용 (Redis에 저장되는 JSON 형태 통일)
        val serializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        // 모든 캐시에 공통 적용할 기본 설정
        // RedisCacheConfiguration은 불변 객체 → 메서드 호출마다 새 객체를 반환하므로 체이닝으로 설정을 쌓아감
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            // 캐시 키를 문자열로 직렬화 → Redis에서 "nearby-stores::37.5:127.0:500.0" 형태로 보임
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            // 캐시 값을 JSON으로 직렬화
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            // null 캐싱 금지
            // null을 캐싱하면 이후 실제 데이터가 생겨도 캐시가 null을 반환하는 문제 발생
            .disableCachingNullValues()

        return RedisCacheManager.builder(connectionFactory)
            // withCacheConfiguration에 명시되지 않은 캐시의 fallback 설정
            .cacheDefaults(defaultConfig)
            // defaultConfig는 불변이므로 entryTtl()은 TTL만 다른 새 객체를 반환
            // 즉, defaultConfig의 직렬화 설정은 그대로 유지하고 TTL만 덮어씀
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
