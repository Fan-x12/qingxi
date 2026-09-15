package com.example.videoparser

// Distance is measured from the current layout, never from gesture accumulation.
internal fun shouldShowChatBottom(detached: Boolean, distance: Float, latestBelowViewport: Boolean,
    showDistance: Float, hideDistance: Float, wasVisible: Boolean): Boolean =
    detached && (latestBelowViewport || distance > if (wasVisible) hideDistance else showDistance)
