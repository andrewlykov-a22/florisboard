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

import java.util.TreeMap
import kotlin.math.abs

/**
 * Pure, unit-testable suggestion algorithms for the Latin (Cyrillic) provider.
 * No Android dependencies.
 */
object LatinSuggestionAlgorithms {

    fun prefixMatches(words: TreeMap<String, Int>, prefix: String, limit: Int): List<Pair<String, Int>> {
        if (prefix.isBlank()) return emptyList()
        val tail = words.tailMap(prefix)
        val result = ArrayList<Pair<String, Int>>(limit)
        for ((word, freq) in tail) {
            if (!word.startsWith(prefix)) break
            if (word == prefix) continue
            result.add(word to freq)
            if (result.size >= limit * 4) break
        }
        result.sortByDescending { it.second }
        return result.take(limit)
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    /** Words close to [word] by edit distance (same first letter, similar length). */
    fun fuzzyMatches(words: TreeMap<String, Int>, word: String, limit: Int): List<Pair<String, Int>> {
        if (word.isBlank()) return emptyList()
        val maxDist = if (word.length >= 5) 2 else 1
        val first = word.first()
        val result = ArrayList<Pair<String, Int>>()
        for ((candidate, freq) in words.tailMap(first.toString())) {
            if (candidate.first() != first) break
            if (abs(candidate.length - word.length) > maxDist) continue
            if (levenshtein(candidate, word) <= maxDist) {
                result.add(candidate to freq)
            }
        }
        result.sortByDescending { it.second }
        return result.take(limit)
    }

    /** Score boost for words the user has previously used after the predecessor. */
    fun bigramBoost(predictedForPrev: List<String>, word: String): Int {
        val index = predictedForPrev.indexOf(word)
        return if (index < 0) 0 else (predictedForPrev.size - index) * 1000
    }

    /**
     * Whether the best fuzzy correction may be auto-committed on a word separator.
     * Only with independent evidence: the candidate continues a known bigram with the
     * previous word, or it is the single unambiguous match. Ambiguous guesses are
     * shown in the candidate row only — never silently substituted.
     */
    fun isFuzzyAutoCommitAllowed(fuzzy: List<String>, predictedForPrev: List<String>): Boolean {
        val best = fuzzy.firstOrNull() ?: return false
        return best in predictedForPrev || fuzzy.size == 1
    }
}
