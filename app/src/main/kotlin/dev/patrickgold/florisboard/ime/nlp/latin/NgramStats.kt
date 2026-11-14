/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple in-memory bigram model (prev word -> next word -> count), persisted as
 * JSON per language and continuously trained on accepted/reverted suggestions.
 */
class NgramStats(private val context: Context) {
    private val lock = Any()
    private val data = mutableMapOf<String, MutableMap<String, MutableMap<String, Int>>>()
    private val loadedLangs = mutableSetOf<String>()

    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer()))

    private fun fileFor(lang: String): File = File(context.filesDir, "ngrams_$lang.json")

    private fun load(lang: String) {
        if (lang in loadedLangs) return
        loadedLangs.add(lang)
        val file = fileFor(lang)
        if (!file.exists()) return
        try {
            val decoded = Json.decodeFromString(serializer, file.readText())
            data[lang] = decoded.mapValues { it.value.toMutableMap() }.toMutableMap()
        } catch (_: Exception) {
            data.remove(lang)
        }
    }

    private fun save(lang: String) {
        val snapshot = synchronized(lock) { data[lang]?.mapValues { it.value.toMap() } ?: return }
        try {
            fileFor(lang).writeText(Json.encodeToString(serializer, snapshot))
        } catch (_: Exception) {
            // Ignore persist failures, stats are best-effort
        }
    }

    /** Returns up to [limit] next-word predictions for the given previous word. */
    fun predict(lang: String, prev: String, limit: Int): List<String> {
        synchronized(lock) {
            load(lang)
            val successors = data[lang]?.get(prev) ?: return emptyList()
            return successors.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        }
    }

    /** Adjusts the bigram count for ([prev] -> [word]) by [delta]. */
    fun record(lang: String, prev: String, word: String, delta: Int) {
        if (prev.isBlank() || word.isBlank()) return
        synchronized(lock) {
            load(lang)
            val langData = data.getOrPut(lang) { mutableMapOf() }
            val successors = langData.getOrPut(prev) { mutableMapOf() }
            val newValue = (successors[word] ?: 0) + delta
            if (newValue <= 0) {
                successors.remove(word)
            } else {
                successors[word] = newValue
            }
        }
        save(lang)
    }
}
