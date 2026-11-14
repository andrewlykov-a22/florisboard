/*
 * Copyright (C) 2022-2026 The FlorisBoard Contributors
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
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import java.util.TreeMap

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        private const val MAX_DICTIONARY_WORDS = 60_000
    }

    private val appContext by context.appContext()

    private data class DictEntry(val words: TreeMap<String, Int>)

    private val dicts = guardedByLock { mutableMapOf<String, DictEntry>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val ngramStats = NgramStats(appContext)

    // Word preceding the currently shown candidates, used for bigram training.
    // Not volatile: suggest()/notifySuggestionAccepted() are called from the same NLP scope.
    private var pendingPrevWord: String? = null
    private var lastAcceptedWord: String? = null

    override val providerId = ProviderId

    override suspend fun create() {
        // Nothing to set up globally, dictionaries are loaded lazily in preload().
    }

    private fun loadDict(lang: String): DictEntry {
        val assetPath = when (lang) {
            "en" -> "ime/dict/data.json"
            else -> "ime/dict/$lang.json"
        }
        val rawData = try {
            appContext.assets.readText(assetPath)
        } catch (e: Exception) {
            flogDebug { "no dictionary asset for lang=$lang, falling back to en" }
            appContext.assets.readText("ime/dict/data.json")
        }
        val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
        return DictEntry(TreeMap(jsonData))
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        val lang = subtype.primaryLocale.language
        dicts.withLock { dicts ->
            if (dicts[lang] == null) {
                dicts[lang] = loadDict(lang)
            }
        }
    }

    private fun prefixMatches(dict: DictEntry, prefix: String, limit: Int): List<Pair<String, Int>> {
        if (prefix.isBlank()) return emptyList()
        val tail = dict.words.tailMap(prefix)
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

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val lang = subtype.primaryLocale.language
        val dict = dicts.withLock { it[lang] } ?: return SpellingResult.validWord()
        val lower = word.lowercase()
        if (dict.words.containsKey(lower) || dict.words.containsKey(word)) {
            return SpellingResult.validWord()
        }
        val suggestions = prefixMatches(dict, lower, maxSuggestionCount).map { it.first }.toTypedArray()
        return if (suggestions.isEmpty()) {
            SpellingResult.validWord()
        } else {
            SpellingResult.typo(suggestions)
        }
    }

    private fun prevWordOf(content: EditorContent, composing: String): String? {
        var before = content.textBeforeSelection
        if (composing.isNotEmpty() && before.endsWith(composing, ignoreCase = true)) {
            before = before.substring(0, before.length - composing.length)
        }
        return Regex("[\\p{L}\\p{N}]+").findAll(before).lastOrNull()?.value?.lowercase()
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val lang = subtype.primaryLocale.language
        val dict = dicts.withLock { it[lang] } ?: return emptyList()
        val rawComposing = content.composingText.trim()
        val composing = rawComposing.lowercase()
        val prevWord = prevWordOf(content, rawComposing)
        pendingPrevWord = prevWord
        if (composing.isBlank() || composing.any { it.isWhitespace() }) {
            // Next-word prediction: only right after an explicit word separator
            // (space/punctuation), which is the user's clear "new word" marker.
            val before = content.textBeforeSelection
            val separatorTyped = before.isNotEmpty() && !before.last().isLetterOrDigit()
            if (prevWord != null && separatorTyped) {
                val predicted = ngramStats.predict(lang, prevWord, maxCandidateCount)
                if (predicted.isNotEmpty()) {
                    val freqOf = dict.words
                    val sorted = predicted.sortedByDescending { freqOf[it] ?: 0 }
                    return sorted.mapIndexed { index, word ->
                        WordSuggestionCandidate(
                            text = word,
                            secondaryText = null,
                            confidence = (0.6 - index * 0.05).coerceAtLeast(0.1),
                            isEligibleForAutoCommit = false,
                            sourceProvider = this@LatinLanguageProvider,
                        )
                    }
                }
            }
            return emptyList()
        }
        val prev = prevWord
        val predictedForPrev = prev?.let { ngramStats.predict(lang, it, 64) } ?: emptyList()
        val matches = prefixMatches(dict, composing, maxCandidateCount * 2)
            .sortedByDescending { (word, freq) ->
                // Boost words the user has previously chosen after this predecessor
                val boost = predictedForPrev.indexOf(word).let { if (it < 0) 0 else (predictedForPrev.size - it) * 1000 }
                freq + boost
            }
            .take(maxCandidateCount)
        val composingIsValidWord = dict.words.containsKey(composing)
        return matches.mapIndexed { index, (word, freq) ->
            WordSuggestionCandidate(
                text = word,
                secondaryText = null,
                confidence = (1.0 - index * 0.05).coerceAtLeast(0.1),
                // Auto-commit may only correct a typo (a word not in the dictionary).
                // If the user typed a complete valid word, space/punctuation must
                // simply be inserted, never replace the word ("сын," -> "сына").
                isEligibleForAutoCommit = index == 0 && word.length == composing.length + 1 && !composingIsValidWord,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.lowercase()
        val prev = pendingPrevWord ?: lastAcceptedWord
        if (prev != null && prev != word) {
            ngramStats.record(subtype.primaryLocale.language, prev, word, delta = 1)
        }
        lastAcceptedWord = word
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.lowercase()
        val prev = pendingPrevWord ?: lastAcceptedWord
        if (prev != null && prev != word) {
            ngramStats.record(subtype.primaryLocale.language, prev, word, delta = -1)
        }
        if (lastAcceptedWord == word) {
            lastAcceptedWord = null
        }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        val lang = subtype.primaryLocale.language
        return dicts.withLock { it[lang]?.words?.keys?.toList() ?: emptyList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val lang = subtype.primaryLocale.language
        return dicts.withLock { it[lang]?.words?.get(word) ?: 0 } / 255.0
    }

    override suspend fun destroy() {
        dicts.withLock { it.clear() }
    }
}
