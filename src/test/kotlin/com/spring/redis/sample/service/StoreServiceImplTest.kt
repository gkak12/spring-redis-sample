package com.spring.redis.sample.service

import com.spring.redis.sample.dto.store.StoreWithDistance
import com.spring.redis.sample.redis.CacheStampedeProtector
import com.spring.redis.sample.repository.StoreRepository
import com.spring.redis.sample.service.impl.StoreServiceImpl
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

@ExtendWith(MockitoExtension::class)
@DisplayName("StoreServiceImpl - 매장 조회 Cache Stampede 방지")
class StoreServiceImplTest {

    @Mock private lateinit var storeRepository: StoreRepository
    @Mock private lateinit var stampedeProtector: CacheStampedeProtector

    private lateinit var service: StoreServiceImpl

    companion object {
        private val STORE = StoreWithDistance(
            id = 1L, name = "스타벅스 강남점",
            address = "서울 강남구", latitude = 37.5, longitude = 127.0,
            category = "CAFE", phone = "02-1234-5678", distance = 120.5
        )
    }

    @BeforeEach
    fun setUp() {
        service = StoreServiceImpl(storeRepository, stampedeProtector)
    }

    @Test
    @DisplayName("캐시 이름이 'nearby-stores', 키가 'lat:lng:radius' 형태로 설정된다")
    fun `should use correct cache name and cache key`() {
        whenever(stampedeProtector.get(eq("nearby-stores"), eq("37.5:127.0:500.0"), any<() -> Any>()))
            .thenReturn(emptyList<Any>())

        service.findNearbyStores(37.5, 127.0, 500.0)

        verify(stampedeProtector).get(eq("nearby-stores"), eq("37.5:127.0:500.0"), any())
    }

    @Test
    @DisplayName("Cache Miss 시 DB를 조회하고 StoreResponse로 변환한다")
    fun `should query DB and map to StoreResponse on cache miss`() {
        // loader를 실제로 실행해 StoreServiceImpl 내부 로직 검증
        whenever(stampedeProtector.get(any(), any(), any<() -> Any>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<() -> Any>(2)).invoke()
        }
        whenever(storeRepository.findStoresWithinRadius(37.5, 127.0, 500.0))
            .thenReturn(listOf(STORE))

        val result = service.findNearbyStores(37.5, 127.0, 500.0)

        assertThat(result).hasSize(1)
        with(result[0]) {
            assertThat(id).isEqualTo(STORE.id)
            assertThat(name).isEqualTo(STORE.name)
            assertThat(address).isEqualTo(STORE.address)
            assertThat(distanceMeters).isEqualTo(121) // 120.5.roundToInt()
        }
    }

    @Test
    @DisplayName("반경 내 매장이 없으면 빈 리스트를 반환한다")
    fun `should return empty list when no stores within radius`() {
        whenever(stampedeProtector.get(any(), any(), any<() -> Any>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<() -> Any>(2)).invoke()
        }
        whenever(storeRepository.findStoresWithinRadius(any(), any(), any()))
            .thenReturn(emptyList())

        val result = service.findNearbyStores(37.5, 127.0, 500.0)

        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("여러 매장이 있으면 모두 StoreResponse로 변환된다")
    fun `should map all stores to StoreResponse`() {
        val store2 = STORE.copy(id = 2L, name = "투썸플레이스", distance = 300.0)
        whenever(stampedeProtector.get(any(), any(), any<() -> Any>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<() -> Any>(2)).invoke()
        }
        whenever(storeRepository.findStoresWithinRadius(any(), any(), any()))
            .thenReturn(listOf(STORE, store2))

        val result = service.findNearbyStores(37.5, 127.0, 500.0)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.name }).containsExactly("스타벅스 강남점", "투썸플레이스")
    }
}
