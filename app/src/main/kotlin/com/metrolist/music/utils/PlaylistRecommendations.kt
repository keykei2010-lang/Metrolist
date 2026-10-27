package com.metrolist.music.utils

/** Take turns across seed radios so the first seed cannot dominate suggestions. */
internal fun <T> blendRecommendations(
    radios: List<List<T>>,
    excludedIds: Set<String>,
    limit: Int,
    id: (T) -> String,
): List<T> {
    if (limit <= 0) return emptyList()
    val seen = excludedIds.toMutableSet()
    return buildList {
        for (index in 0 until (radios.maxOfOrNull { it.size } ?: 0)) {
            for (radio in radios) {
                val item = radio.getOrNull(index) ?: continue
                if (seen.add(id(item))) add(item)
                if (size == limit) return@buildList
            }
        }
    }
}
