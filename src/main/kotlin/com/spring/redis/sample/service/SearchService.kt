package com.spring.redis.sample.service

import com.spring.redis.sample.dto.search.TrendingKeyword

interface SearchService {
    fun recordSearch(keyword: String)
    fun getTrendingKeywords(topN: Long = TOP_N): List<TrendingKeyword>

    companion object {
        const val TOP_N = 10L
    }
}
