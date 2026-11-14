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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatinSuggestionAlgorithmsTest {
    private fun dictOf(vararg entries: Pair<String, Int>) =
        TreeMap(entries.toMap())

    @Test
    fun `levenshtein basic distances`() {
        assertEquals(0, LatinSuggestionAlgorithms.levenshtein("сын", "сын"))
        assertEquals(1, LatinSuggestionAlgorithms.levenshtein("сын", "сым"))
        assertEquals(2, LatinSuggestionAlgorithms.levenshtein("сын", "сынок"))
        assertEquals(3, LatinSuggestionAlgorithms.levenshtein("abc", "def"))
        assertEquals(1, LatinSuggestionAlgorithms.levenshtein("привет", "привит"))
    }

    @Test
    fun `prefix matches sorted by frequency and excludes exact word`() {
        val dict = dictOf("сын" to 200, "сына" to 100, "сынок" to 50)
        val matches = LatinSuggestionAlgorithms.prefixMatches(dict, "сын", 10).map { it.first }
        assertEquals(listOf("сына", "сынок"), matches)
    }

    @Test
    fun `fuzzy finds corrections within distance one`() {
        val dict = dictOf("сын" to 200, "сам" to 255, "сынок" to 50, "привет" to 100)
        val matches = LatinSuggestionAlgorithms.fuzzyMatches(dict, "сым", 10).map { it.first }
        assertTrue("сын" in matches)
        assertTrue("сам" in matches)
        assertFalse("сынок" in matches) // length diff 2 > maxDist 1
        assertFalse("привет" in matches) // different first letter
    }

    @Test
    fun `fuzzy allows distance two for words of three letters and more`() {
        val dict = dictOf("привет" to 100, "привязь" to 10, "сын" to 200, "сяо" to 149)
        val longMatches = LatinSuggestionAlgorithms.fuzzyMatches(dict, "привит", 10).map { it.first }
        assertTrue("привет" in longMatches)
        assertFalse("привязь" in longMatches) // distance 3
        val shortMatches = LatinSuggestionAlgorithms.fuzzyMatches(dict, "ссы", 10).map { it.first }
        assertTrue("сын" in shortMatches) // distance 2, now reachable for 3-letter words
    }

    @Test
    fun `reliable word requires minimum frequency`() {
        val dict = dictOf("сын" to 253, "ссы" to 43, "мой" to 255)
        assertTrue(LatinSuggestionAlgorithms.isReliableWord(dict, "сын"))
        assertTrue(LatinSuggestionAlgorithms.isReliableWord(dict, "мой"))
        assertFalse(LatinSuggestionAlgorithms.isReliableWord(dict, "ссы")) // rare word stays correctable
        assertFalse(LatinSuggestionAlgorithms.isReliableWord(dict, "совсемнет"))
    }

    @Test
    fun `bigram boost favors known successors`() {
        val predicted = listOf("сын", "сам")
        assertTrue(LatinSuggestionAlgorithms.bigramBoost(predicted, "сын") > LatinSuggestionAlgorithms.bigramBoost(predicted, "сам"))
        assertEquals(0, LatinSuggestionAlgorithms.bigramBoost(predicted, "нет"))
    }

    @Test
    fun `auto-commit allowed only with evidence`() {
        fun f(vararg entries: Triple<String, Int, Int>) = entries.toList()
        // Ambiguous tie: two candidates at the same minimal distance, no context -> no auto-commit
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(
            f(Triple("сам", 255, 1), Triple("сын", 200, 1)), emptyList()))
        // Single unambiguous match -> allowed
        assertTrue(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(
            f(Triple("привет", 100, 1)), emptyList()))
        // Distance margin: best is strictly closer than the runner-up -> allowed
        assertTrue(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(
            f(Triple("привет", 100, 1), Triple("прийти", 90, 2)), emptyList()))
        // Best candidate continues a known bigram -> allowed
        assertTrue(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(
            f(Triple("сын", 200, 1), Triple("сам", 255, 1)), listOf("сын")))
        // Best candidate is NOT in bigrams even though a worse one is -> no
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(
            f(Triple("сам", 255, 1), Triple("сын", 200, 1)), listOf("сын")))
        // Empty -> no
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(emptyList(), emptyList()))
    }
}
