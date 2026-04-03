package com.spring.redis.sample.repository.impl

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import com.spring.redis.sample.dto.store.StoreWithDistance
import com.spring.redis.sample.entity.QStore
import com.spring.redis.sample.repository.StoreRepositoryDsl
import org.springframework.stereotype.Repository

@Repository
class StoreRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory
) : StoreRepositoryDsl {

    override fun findStoresWithinRadius(lat: Double, lng: Double, radius: Double): List<StoreWithDistance> {
        val store = QStore.store

        val distanceExpr = Expressions.numberTemplate(
            Double::class.java,
            "ST_Distance_Sphere(POINT({0}, {1}), POINT({2}, {3}))",
            store.longitude, store.latitude, lng, lat
        )

        return queryFactory
            .select(
                Projections.constructor(
                    StoreWithDistance::class.java,
                    store.id,
                    store.name,
                    store.address,
                    store.latitude,
                    store.longitude,
                    store.category.stringValue(),
                    store.phone,
                    distanceExpr
                )
            )
            .from(store)
            .where(distanceExpr.loe(radius))
            .orderBy(distanceExpr.asc())
            .fetch()
    }
}
