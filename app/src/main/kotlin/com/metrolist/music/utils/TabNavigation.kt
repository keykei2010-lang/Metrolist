package com.metrolist.music.utils

internal fun swipedTabIndex(index: Int, count: Int, distance: Float, threshold: Float, rtl: Boolean): Int? {
    if (index !in 0 until count || !distance.isFinite() || threshold <= 0f || kotlin.math.abs(distance) < threshold) return null
    val direction = (if (distance < 0) 1 else -1) * (if (rtl) -1 else 1)
    return (index + direction).takeIf { it in 0 until count }
}
