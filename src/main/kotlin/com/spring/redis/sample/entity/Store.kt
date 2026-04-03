package com.spring.redis.sample.entity

import jakarta.persistence.*

@Entity
@Table(name = "stores")
class Store(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false)
    var address: String,

    // 위도 (latitude): 북위/남위 (-90 ~ 90)
    @Column(nullable = false)
    var latitude: Double,

    // 경도 (longitude): 동경/서경 (-180 ~ 180)
    @Column(nullable = false)
    var longitude: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: StoreCategory = StoreCategory.CONVENIENCE_STORE,

    @Column(length = 20)
    var phone: String? = null
)

enum class StoreCategory {
    CONVENIENCE_STORE, CAFE, RESTAURANT, PHARMACY, OTHER
}
