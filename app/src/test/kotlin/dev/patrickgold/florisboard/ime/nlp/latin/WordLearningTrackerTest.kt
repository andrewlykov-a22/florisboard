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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordLearningTrackerTest {
    private fun event(text: String = "привет сын", session: Long = 1, language: String = "ru") =
        WordLearningContext.completed(language, session, text, 0, "привет", "сын", false)!!

    @Test fun `acceptance separator and repeated queries count once`() {
        val tracker = WordLearningTracker()
        var count = 0
        tracker.record(event()) { count += it }
        tracker.record(event("Привет сын, ")) { count += it }
        tracker.record(event("привет сын,  ")) { count += it }
        assertEquals(1, count)
    }

    @Test fun `later occurrence and new session each train`() {
        val tracker = WordLearningTracker()
        var count = 0
        tracker.record(event()) { count += it }
        tracker.record(event("привет сын привет сын")) { count += it }
        tracker.record(event(session = 2)) { count += it }
        tracker.record(event(language = "en")) { count += it }
        assertEquals(4, count)
    }

    @Test fun `revert counts once and delayed callbacks do not relearn`() {
        val tracker = WordLearningTracker()
        var count = 0
        tracker.record(event()) { count += it }
        repeat(2) { tracker.record(event(), reverted = true) { count += it } }
        tracker.record(event()) { count += it }
        assertEquals(0, count)
    }

    @Test fun `revert before delayed acceptance cancels learning`() {
        val tracker = WordLearningTracker()
        var count = 0
        tracker.record(event(), reverted = true) { count += it }
        tracker.record(event()) { count += it }
        assertEquals(0, count)
    }

    @Test fun `private sessions and absent predecessors produce no learning event`() {
        assertNull(WordLearningContext.completed("ru", 1, "привет сын", 0, "привет", "сын", true))
        assertNull(WordLearningContext.completed("ru", 1, "сын", 0, null, "сын", false))
    }

    @Test fun `snapshot offset is part of completed word identity`() {
        val event = WordLearningContext.completed("ru", 1, "привет сын, ", 100, "привет", "сын", false)!!
        assertEquals(110, event.wordEndOffset)
    }
}
