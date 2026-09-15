package com.example.videoparser.domain

import com.example.videoparser.data.provider.ParseProvider
import kotlinx.coroutines.CancellationException

/** 按平台筛选接口，依次尝试解析并汇总失败原因。 */
class ParseCoordinator(private val providers: List<ParseProvider>) {
    suspend fun parse(
        request: ParseRequest,
        selectedProviderName: String? = null,
        onAttempt: (index: Int, provider: ParseProvider) -> Unit
    ): Result<ParseResult> {
        val supportedProviders = providers.filter {
            request.platform in it.supportedPlatforms && MediaType.VIDEO in it.supportedMediaTypes
        }
        // 手动选择只请求指定接口；自动模式按注册顺序串行回退。
        val candidates = if (selectedProviderName.isNullOrBlank()) {
            supportedProviders
        } else {
            supportedProviders.filter { it.name == selectedProviderName }
        }
        if (candidates.isEmpty()) {
            val message = if (selectedProviderName.isNullOrBlank()) {
                "暂无支持该平台的解析接口"
            } else {
                "选择的解析接口暂不支持该平台"
            }
            return Result.failure(IllegalStateException(message))
        }
        val failures = mutableListOf<String>()
        candidates.forEachIndexed { index, provider ->
            onAttempt(index + 1, provider)
            try {
                val result = provider.parse(request)
                if (result.isSuccess) {
                    return result.map { it.copy(providerName = provider.name) }
                }
                failures += "${provider.name}: ${result.exceptionOrNull()?.message ?: "返回无效数据"}"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                failures += "${provider.name}: ${error.message ?: "请求失败"}"
            }
        }
        return Result.failure(ParseAllProvidersException(failures))
    }
}

class ParseAllProvidersException(val failures: List<String>) : Exception("所有解析接口均失败")
