package com.example.videoparser

// 距离由当前布局计算，不累加手势位移。
internal fun shouldShowChatBottom(detached: Boolean, distance: Float, latestBelowViewport: Boolean,
    showDistance: Float, hideDistance: Float, wasVisible: Boolean): Boolean =
    detached && (latestBelowViewport || distance > if (wasVisible) hideDistance else showDistance)
