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

    /** Best fuzzy candidates with their edit distance, best first. */
    fun fuzzyMatchesWithDistance(words: TreeMap<String, Int>, word: String, limit: Int): List<Triple<String, Int, Int>> {
        if (word.isBlank()) return emptyList()
        val maxDist = if (word.length >= 3) 2 else 1
        val first = word.first()
        val result = ArrayList<Triple<String, Int, Int>>()
        for ((candidate, freq) in words.tailMap(first.toString())) {
            if (candidate.first() != first) break
            if (abs(candidate.length - word.length) > maxDist) continue
            val dist = levenshtein(candidate, word)
            if (dist in 1..maxDist) {
                result.add(Triple(candidate, freq, dist))
            }
        }
        result.sortWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
        return result.take(limit)
    }

    /**
     * A composing word counts as valid (and thus protected from auto-correction) only
     * if it is a reasonably frequent dictionary word. Rare dictionary artifacts like
     * «ссы» stay correctable — the evidence gate still guards ambiguous cases.
     */
    fun isReliableWord(words: TreeMap<String, Int>, word: String, minFreq: Int = 48): Boolean =
        (words[word] ?: 0) >= minFreq

    /** Words close to [word] by edit distance (same first letter, similar length). */
    fun fuzzyMatches(words: TreeMap<String, Int>, word: String, limit: Int): List<Pair<String, Int>> =
        fuzzyMatchesWithDistance(words, word, limit).map { it.first to it.second }

    /** Score boost for words the user has previously used after the predecessor. */
    fun bigramBoost(predictedForPrev: List<String>, word: String): Int {
        val index = predictedForPrev.indexOf(word)
        return if (index < 0) 0 else (predictedForPrev.size - index) * 1000
    }

    /**
     * Whether the best fuzzy correction may be auto-committed on a word separator.
     * Only with independent evidence: the candidate continues a known bigram with the
     * previous word, is the single unambiguous match, or is strictly closer than the
     * runner-up (distance margin). Ambiguous ties like "сым" -> "сам"/"сын" are shown
     * in the candidate row only — never silently substituted.
     */
    fun isFuzzyAutoCommitAllowed(
        fuzzyWithDistance: List<Triple<String, Int, Int>>,
        predictedForPrev: List<String>,
    ): Boolean {
        val best = fuzzyWithDistance.firstOrNull() ?: return false
        if (best.first in predictedForPrev) return true
        if (fuzzyWithDistance.size == 1) return true
        return best.third < fuzzyWithDistance[1].third
    }
}
