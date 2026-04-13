package com.spring.redis.sample.service

import com.spring.redis.sample.entity.Store
import com.spring.redis.sample.entity.StoreCategory
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.impl.StoreGeoServiceImpl
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.geo.Distance
import org.springframework.data.geo.GeoResult
import org.springframework.data.geo.GeoResults
import org.springframework.data.geo.Point
import org.springframework.data.redis.connection.RedisGeoCommands
import org.springframework.data.redis.core.GeoOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.domain.geo.Metrics

@ExtendWith(MockitoExtension::class)
@DisplayName("StoreGeoServiceImpl - Redis Geo 기반 매장 검색")
class StoreGeoServiceImplTest {

    @Mock private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Mock private lateinit var geoOps: GeoOperations<String, Any>
    @Mock private lateinit var storeRepository: StoreRepository

    private lateinit var service: StoreGeoServiceImpl

    companion object {
        private const val GEO_KEY = "stores:geo"

        private val STORE = Store(
            id = 1L, name = "스타벅스 강남점",
            address = "서울 강남구 테헤란로 1",
            latitude = 37.5, longitude = 127.0,
            category = StoreCategory.CAFE, phone = "02-1234-5678"
        )
    }

    @BeforeEach
    fun setUp() {
        service = StoreGeoServiceImpl(redisTemplate, storeRepository)
        whenever(redisTemplate.opsForGeo()).thenReturn(geoOps)
    }

    @Nested
    @DisplayName("addStore()")
    inner class AddStore {

        @Test
        @DisplayName("GEOADD 명령어로 매장 좌표를 등록한다 (경도, 위도 순서)")
        fun `should add store coordinates with longitude latitude order`() {
            service.addStore(STORE)

            // Redis Geo는 Point(경도, 위도) 순서 — 위도·경도와 반대
            val pointCaptor = argumentCaptor<Point>()
            verify(geoOps).add(eq(GEO_KEY), pointCaptor.capture(), eq(STORE.id.toString()))
            assertThat(pointCaptor.firstValue.x).isEqualTo(STORE.longitude) // x = 경도
            assertThat(pointCaptor.firstValue.y).isEqualTo(STORE.latitude)  // y = 위도
        }

        @Test
        @DisplayName("storeId를 문자열로 변환하여 멤버 이름으로 사용한다")
        fun `should use storeId as string member name`() {
            service.addStore(STORE)

            verify(geoOps).add(eq(GEO_KEY), any<Point>(), eq("1"))
        }
    }

    @Nested
    @DisplayName("findNearbyStores()")
    inner class FindNearbyStores {

        @Test
        @DisplayName("GEOSEARCH 결과가 null이면 빈 리스트를 반환한다")
        fun `should return empty list when geo search returns null`() {
            whenever(geoOps.search(any(), any(), any(), any<RedisGeoCommands.GeoSearchCommandArgs>()))
                .thenReturn(null)

            val result = service.findNearbyStores(37.5, 127.0, 500.0)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("GEOSEARCH 결과를 DB에서 조회한 매장 정보와 조합하여 반환한다")
        fun `should combine geo results with DB store info`() {
            val geoLocation = RedisGeoCommands.GeoLocation<Any>("1", Point(127.0, 37.5))
            val geoResult = GeoResult(geoLocation, Distance(150.0, Metrics.METERS))
            val geoResults = GeoResults(listOf(geoResult))

            whenever(geoOps.search(any(), any(), any(), any<RedisGeoCommands.GeoSearchCommandArgs>()))
                .thenReturn(geoResults)
            whenever(storeRepository.findAllById(listOf(1L)))
                .thenReturn(listOf(STORE))

            val result = service.findNearbyStores(37.5, 127.0, 500.0)

            assertThat(result).hasSize(1)
            with(result[0]) {
                assertThat(id).isEqualTo(1L)
                assertThat(name).isEqualTo("스타벅스 강남점")
                assertThat(distanceMeters).isEqualTo(150) // 150.0.roundToInt()
            }
        }

        @Test
        @DisplayName("Redis에는 있지만 DB에 없는 storeId는 결과에서 제외된다")
        fun `should skip store when not found in DB`() {
            // Redis에 storeId=99가 있지만 DB에는 없는 상황 (삭제된 매장)
            val geoLocation = RedisGeoCommands.GeoLocation<Any>("99", Point(127.0, 37.5))
            val geoResult = GeoResult(geoLocation, Distance(100.0, Metrics.METERS))
            val geoResults = GeoResults(listOf(geoResult))

            whenever(geoOps.search(any(), any(), any(), any<RedisGeoCommands.GeoSearchCommandArgs>()))
                .thenReturn(geoResults)
            whenever(storeRepository.findAllById(listOf(99L)))
                .thenReturn(emptyList()) // DB에 없음

            val result = service.findNearbyStores(37.5, 127.0, 500.0)

            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("여러 매장이 있으면 Redis 결과 순서(거리 오름차순)를 유지한다")
        fun `should preserve geo result order (ascending distance)`() {
            val store2 = STORE.copy(id = 2L, name = "투썸플레이스")
            val loc1 = RedisGeoCommands.GeoLocation<Any>("1", Point(127.0, 37.5))
            val loc2 = RedisGeoCommands.GeoLocation<Any>("2", Point(127.01, 37.51))
            val geoResults = GeoResults(listOf(
                GeoResult(loc1, Distance(100.0, Metrics.METERS)),  // 가까운 순
                GeoResult(loc2, Distance(500.0, Metrics.METERS))
            ))

            whenever(geoOps.search(any(), any(), any(), any<RedisGeoCommands.GeoSearchCommandArgs>()))
                .thenReturn(geoResults)
            whenever(storeRepository.findAllById(listOf(1L, 2L)))
                .thenReturn(listOf(STORE, store2))

            val result = service.findNearbyStores(37.5, 127.0, 1000.0)

            assertThat(result).hasSize(2)
            assertThat(result[0].id).isEqualTo(1L)    // 가까운 매장 먼저
            assertThat(result[1].id).isEqualTo(2L)
            assertThat(result[0].distanceMeters).isEqualTo(100)
            assertThat(result[1].distanceMeters).isEqualTo(500)
        }

        @Test
        @DisplayName("DB 일괄 조회로 storeId 목록을 IN 쿼리 1번으로 처리한다 (N+1 방지)")
        fun `should batch fetch stores with single DB query`() {
            val loc1 = RedisGeoCommands.GeoLocation<Any>("1", Point(127.0, 37.5))
            val loc2 = RedisGeoCommands.GeoLocation<Any>("2", Point(127.01, 37.51))
            val geoResults = GeoResults(listOf(
                GeoResult(loc1, Distance(100.0, Metrics.METERS)),
                GeoResult(loc2, Distance(200.0, Metrics.METERS))
            ))

            whenever(geoOps.search(any(), any(), any(), any<RedisGeoCommands.GeoSearchCommandArgs>()))
                .thenReturn(geoResults)
            whenever(storeRepository.findAllById(any<List<Long>>())).thenReturn(emptyList())

            service.findNearbyStores(37.5, 127.0, 500.0)

            // findAllById 1번만 호출됨 (N+1 발생하지 않음)
            verify(storeRepository).findAllById(listOf(1L, 2L))
        }
    }
}
