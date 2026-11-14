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

package dev.patrickgold.florisboard.ime.nlp

/** A completed word's identity and predecessor, captured before asynchronous callbacks. */
data class WordLearningContext(
    val language: String,
    val sessionId: Long,
    val textBeforeWordEnd: String,
    val wordEndOffset: Int,
    val previousWord: String,
    val word: String,
) {
    companion object {
        fun completed(
            language: String, sessionId: Long, textBeforeWordEnd: String, offset: Int,
            previousWord: String?, word: String, isPrivateSession: Boolean,
        ): WordLearningContext? {
            if (isPrivateSession || previousWord == null || previousWord == word) return null
            val completed = textBeforeWordEnd.trimEnd { !it.isLetterOrDigit() && it != '\'' }.lowercase()
            return WordLearningContext(
                language, sessionId, completed, offset.coerceAtLeast(0) + completed.length,
                previousWord, word,
            )
        }
    }
}
