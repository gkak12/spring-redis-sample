package com.spring.redis.sample.repository

import com.spring.redis.sample.entity.StoreLike
import com.spring.redis.sample.entity.StoreLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface StoreLikeRepository : JpaRepository<StoreLike, StoreLikeId> {

    fun findAllByStoreId(storeId: Long): List<StoreLike>

    fun countByStoreId(storeId: Long): Long

    fun existsByStoreIdAndUsername(storeId: Long, username: String): Boolean

    @Modifying
    @Query("DELETE FROM StoreLike sl WHERE sl.storeId = :storeId AND sl.username IN :usernames")
    fun deleteAllByStoreIdAndUsernameIn(storeId: Long, usernames: Set<String>)
}
