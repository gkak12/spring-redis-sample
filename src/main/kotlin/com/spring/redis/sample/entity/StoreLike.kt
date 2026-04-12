package com.spring.redis.sample.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

/**
 * 매장 좋아요 영구 저장 엔티티
 *
 * Write-Behind 패턴:
 * - Redis Set이 실시간 원본(source of truth)
 * - 이 테이블은 Redis 장애 대비용 영구 저장소
 * - StoreLikeSyncScheduler가 1분마다 Redis → DB 동기화
 */
@Entity
@Table(name = "store_likes")
@IdClass(StoreLikeId::class)
class StoreLike(

    @Id
    @Column(name = "store_id", nullable = false)
    val storeId: Long,

    @Id
    @Column(nullable = false, length = 50)
    val username: String,

    @Column(nullable = false)
    val likedAt: LocalDateTime = LocalDateTime.now()
)

data class StoreLikeId(
    val storeId: Long = 0,
    val username: String = ""
) : Serializable
