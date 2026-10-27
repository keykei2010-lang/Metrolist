package com.metrolist.music.models

enum class AndroidAutoSection(val id: String) {
    HOME("home"),
    LIKED("liked"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
}

fun serializeSections(sections: List<Pair<AndroidAutoSection, Boolean>>): String =
    sections.joinToString(",") { (section, enabled) -> "${section.id}:$enabled" }

fun deserializeSections(raw: String): List<Pair<AndroidAutoSection, Boolean>> {
    if (raw.isBlank()) return AndroidAutoSection.values().map { it to true }
    val saved = raw.split(",").mapNotNull { token ->
        val parts = token.split(":")
        if (parts.size != 2) return@mapNotNull null
        val section = AndroidAutoSection.values().find { it.id == parts[0] } ?: return@mapNotNull null
        val enabled = parts[1].toBooleanStrictOrNull() ?: true
        section to enabled
    }.distinctBy { it.first }
    return saved + AndroidAutoSection.entries.filter { section -> saved.none { it.first == section } }.map { it to true }
}

