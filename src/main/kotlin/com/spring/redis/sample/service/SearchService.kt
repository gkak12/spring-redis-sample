package com.spring.redis.sample.service

import com.spring.redis.sample.dto.search.TrendingKeyword

interface SearchService {
    fun recordSearch(keyword: String)
    fun getTrendingKeywords(topN: Long = TOP_N): List<TrendingKeyword>

    /**
     * 유저별 최근 검색어 저장 - List LPUSH + LTRIM으로 최대 N개 유지
     */
    fun saveRecentKeyword(username: String, keyword: String)

    /**
     * 유저별 최근 검색어 목록 조회 - List LRANGE로 전체 반환
     */
    fun getRecentKeywords(username: String): List<String>

    /**
     * 유저별 최근 검색어 전체 삭제
     */
    fun clearRecentKeywords(username: String)

    companion object {
        const val TOP_N = 10L
        const val MAX_RECENT = 10L
    }
}
