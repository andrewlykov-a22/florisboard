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

import dev.patrickgold.florisboard.ime.nlp.WordLearningContext

/** Counts a word once when acceptance and separator requests describe the same word. */
internal class WordLearningTracker {
    private val seen = LinkedHashMap<WordLearningContext, Boolean>()

    @Synchronized
    fun record(context: WordLearningContext, reverted: Boolean = false, apply: (Int) -> Unit) {
        if (reverted) {
            if (seen[context] == true) {
                apply(-1)
            }
            // A delayed acceptance must not re-learn an already reverted word.
            seen[context] = false
            if (seen.size > 128) seen.remove(seen.keys.first())
        } else if (!seen.containsKey(context)) {
            apply(1)
            seen[context] = true
            if (seen.size > 128) seen.remove(seen.keys.first())
        }
    }
}
