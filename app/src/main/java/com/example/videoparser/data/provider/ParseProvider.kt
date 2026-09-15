package com.example.videoparser.data.provider

import com.example.videoparser.domain.MediaType
import com.example.videoparser.domain.ParseRequest
import com.example.videoparser.domain.ParseResult
import com.example.videoparser.domain.Platform

/** 解析接口的统一约定，各服务将响应转换为相同的媒体模型。 */
interface ParseProvider {
    val name: String
    val supportedPlatforms: Set<Platform>
    val supportedMediaTypes: Set<MediaType>

    suspend fun parse(request: ParseRequest): Result<ParseResult>
}
