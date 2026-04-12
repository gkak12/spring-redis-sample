package com.spring.redis.sample.service

interface StoreLikeService {

    /**
     * 매장 좋아요
     * Set SADD: 이미 존재하면 추가 안 됨 → 중복 좋아요 방지
     * @return true: 좋아요 성공, false: 이미 좋아요한 상태
     */
    fun like(storeId: Long, username: String): Boolean

    /**
     * 매장 좋아요 취소
     * Set SREM: 존재하지 않으면 무시
     */
    fun unlike(storeId: Long, username: String)

    /**
     * 매장 좋아요 수 조회
     * Set SCARD: Set의 원소 수 반환
     */
    fun getLikeCount(storeId: Long): Long

    /**
     * 특정 유저의 좋아요 여부 확인
     * Set SISMEMBER: O(1) 조회
     */
    fun isLiked(storeId: Long, username: String): Boolean
}
