package com.spring.redis.sample.dto.search

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SearchRequest(
    @field:NotBlank(message = "검색어를 입력해주세요.")
    @field:Size(min = 1, max = 50, message = "검색어는 1~50자 사이여야 합니다.")
    val keyword: String
)
