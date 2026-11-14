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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawSuggestionContextTest {
    @Test fun `previous word follows normal typing`() {
        val context = RawSuggestionContext()
        context.append("Привет сын, привет сын. Привет сым")
        assertEquals("Привет сын, привет сын. Привет ", context.before)
        assertEquals("сым", context.word)
    }

    @Test fun `suffix completion stays editable until separator`() {
        val context = RawSuggestionContext()
        context.append("привет сы")
        context.append("н")
        assertTrue(context.backspace())
        assertEquals("сы", context.word)
        context.append("н ")
        assertEquals("привет сын ", context.before)
        assertEquals("", context.word)
    }

    @Test fun `fuzzy replacement preserves predecessor`() {
        val context = RawSuggestionContext()
        context.append("привет сым")
        repeat(3) { assertTrue(context.backspace()) }
        context.append("сын ")
        assertEquals("привет сын ", context.before)
    }

    @Test fun `deleting separator invalidates context`() {
        val context = RawSuggestionContext()
        context.append("привет сын ")
        val session = context.sessionId
        assertFalse(context.backspace())
        assertEquals("", context.before)
        assertTrue(context.sessionId > session)
    }

    @Test fun `enter starts fresh context even in pasted text`() {
        val context = RawSuggestionContext()
        context.append("привет сын\nновая строка")
        assertEquals("новая ", context.before)
        assertEquals("строка", context.word)
    }

    @Test fun `clear prevents context crossing field language or mode changes`() {
        val context = RawSuggestionContext()
        context.append("привет сын")
        val session = context.sessionId
        context.clear()
        context.append("hello")
        assertEquals("", context.before)
        assertEquals("hello", context.word)
        assertTrue(context.sessionId > session)
    }

    @Test fun `long context remains bounded at word boundary`() {
        val context = RawSuggestionContext()
        repeat(150) { context.append("привет сын ") }
        assertTrue(context.before.length <= 256)
        assertTrue(context.before.startsWith("привет ") || context.before.startsWith("сын "))
        assertTrue(context.before.endsWith("сын "))
    }
}
