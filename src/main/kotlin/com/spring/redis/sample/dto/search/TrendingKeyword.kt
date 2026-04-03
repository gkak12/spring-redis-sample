package com.spring.redis.sample.dto.search

data class TrendingKeyword(
    val rank: Int,
    val keyword: String,
    val score: Long
)
