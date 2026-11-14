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
    fun `fuzzy allows distance two for words of five letters and more`() {
        val dict = dictOf("привет" to 100, "привязь" to 10)
        val matches = LatinSuggestionAlgorithms.fuzzyMatches(dict, "привит", 10).map { it.first }
        assertTrue("привет" in matches)
        assertFalse("привязь" in matches) // distance 3
    }

    @Test
    fun `bigram boost favors known successors`() {
        val predicted = listOf("сын", "сам")
        assertTrue(LatinSuggestionAlgorithms.bigramBoost(predicted, "сын") > LatinSuggestionAlgorithms.bigramBoost(predicted, "сам"))
        assertEquals(0, LatinSuggestionAlgorithms.bigramBoost(predicted, "нет"))
    }

    @Test
    fun `auto-commit allowed only with evidence`() {
        // Ambiguous: two equally plausible corrections, no context -> no auto-commit
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(listOf("сам", "сын"), emptyList()))
        // Single unambiguous match -> allowed
        assertTrue(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(listOf("привет"), emptyList()))
        // Best candidate continues a known bigram -> allowed
        assertTrue(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(listOf("сын", "сам"), listOf("сын")))
        // Best candidate is NOT in bigrams even though a worse one is -> no
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(listOf("сам", "сын"), listOf("сын")))
        // Empty -> no
        assertFalse(LatinSuggestionAlgorithms.isFuzzyAutoCommitAllowed(emptyList(), emptyList()))
    }
}
