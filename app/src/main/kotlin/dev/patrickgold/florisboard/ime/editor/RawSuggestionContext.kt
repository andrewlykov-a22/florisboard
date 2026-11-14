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

package dev.patrickgold.florisboard.ime.editor

/** Text sent through the keyboard to an editor that cannot supply surrounding text. */
internal class RawSuggestionContext {
    private val composing = StringBuilder()
    private val context = StringBuilder()
    @Volatile
    var sessionId: Long = 0
        private set
    val word: String get() = synchronized(this) { composing.toString() }
    val before: String get() = synchronized(this) { context.toString() }

    data class Snapshot(val before: String, val word: String, val sessionId: Long)

    @Synchronized
    fun snapshot() = Snapshot(context.toString(), composing.toString(), sessionId)

    @Synchronized
    fun clear() {
        composing.clear()
        context.clear()
        sessionId++
    }

    @Synchronized
    fun append(text: String) {
        for (char in text) {
            when {
                char == '\n' || char == '\r' -> clear()
                char.isLetterOrDigit() || char == '\'' -> composing.append(char)
                else -> {
                    context.append(composing).append(char)
                    composing.clear()
                    if (context.length > 256) {
                        val boundary = context.indexOf(" ", context.length - 256)
                        if (boundary >= 0) context.delete(0, boundary + 1) else context.clear()
                    }
                }
            }
        }
    }

    @Synchronized
    fun backspace(): Boolean {
        if (composing.isEmpty()) {
            // The new cursor position cannot be inferred beyond the tracked word.
            clear()
            return false
        }
        composing.deleteAt(composing.length - 1)
        return true
    }
}
