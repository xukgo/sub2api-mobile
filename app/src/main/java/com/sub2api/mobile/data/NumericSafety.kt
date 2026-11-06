package com.sub2api.mobile.data

fun safeProgressFraction(percentage: Double?): Float {
    val finite = percentage?.takeIf { it.isFinite() } ?: 0.0
    return (finite / 100.0).coerceIn(0.0, 1.0).toFloat()
}

fun safePercentage(percentage: Double?): Double =
    percentage?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0

fun safeFinite(value: Double, fallback: Double = 0.0): Double =
    value.takeIf { it.isFinite() } ?: fallback

fun resolvePercentage(explicit: Double?, used: Double?, limit: Double?): Double? {
    explicit?.takeIf { it.isFinite() }?.let { return it.coerceIn(0.0, 100.0) }
    val safeLimit = limit?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeUsed = used?.takeIf { it.isFinite() } ?: 0.0
    return ((safeUsed / safeLimit) * 100.0).coerceIn(0.0, 100.0)
}
