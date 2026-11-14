package com.nandox.arcade.catalog

import java.text.Normalizer

object TitleNormalizer {
    private val marks = Regex("\\p{M}+")
    private val tags = Regex("\\(.*?\\)|\\[.*?]")

    private fun tokens(s: String): List<String> =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    fun searchText(title: String): String = tokens(title).joinToString(" ")

    fun likePattern(query: String): String = tokens(query).joinToString("%", "%", "%")

    fun matchKey(fileTitle: String): String = tokens(fileTitle.replace(tags, " ")).joinToString("")
}
