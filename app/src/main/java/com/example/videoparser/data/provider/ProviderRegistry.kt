package com.example.videoparser.data.provider

/** 集中维护接口名称与默认调用顺序，供设置页和解析流程共用。 */
object ProviderRegistry {
    const val YRAIN_NAME = "夜雨聚合解析"
    const val BUGPK_NAME = "BugPk 聚合解析"
    const val BUGPK_PLATFORM_NAME = "BugPk 多平台聚合解析"

    fun displayName(providerName: String): String = when {
        providerName == BUGPK_PLATFORM_NAME -> "BugPk 聚合 API"
        providerName.contains("夜雨", ignoreCase = true) -> "夜雨 API"
        providerName.contains("BugPk", ignoreCase = true) -> "BugPk API"
        else -> providerName
    }

    /** 自动路由使用夜雨优先、BugPk 串行回退；手动模式由 ParseCoordinator 只保留指定节点。 */
    fun default(): List<ParseProvider> = listOf(
        YrainAggregateProvider(),
        BugPkAggregateProvider(),
        BugPkPlatformProvider()
    )
}
