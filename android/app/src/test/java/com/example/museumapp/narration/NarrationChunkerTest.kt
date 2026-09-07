package com.example.museumapp.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationChunkerTest {
    @Test
    fun shortTextReturnsASingleUnchangedChunk() {
        val text = "A short artifact description."
        assertEquals(listOf(text), NarrationChunker.chunk(text, maxLength = 4000))
    }

    @Test
    fun blankOrEmptyTextReturnsNoChunks() {
        assertTrue(NarrationChunker.chunk("", maxLength = 100).isEmpty())
        assertTrue(NarrationChunker.chunk("   ", maxLength = 100).isEmpty())
    }

    @Test
    fun longTextSplitsIntoMultipleChunksEachUnderTheLimit() {
        val sentence = "This artifact was crafted by local artisans using traditional techniques. "
        val text = sentence.repeat(200)
        val maxLength = 500

        val chunks = NarrationChunker.chunk(text, maxLength)

        assertTrue("expected more than one chunk", chunks.size > 1)
        chunks.forEach { chunk -> assertTrue("chunk exceeded max length: ${chunk.length}", chunk.length <= maxLength) }
    }

    @Test
    fun chunkBoundariesLandOnSentenceEndsWherePossible() {
        val text = (1..50).joinToString(" ") { index -> "Sentence number $index ends here." }
        val maxLength = 200

        val chunks = NarrationChunker.chunk(text, maxLength)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue("chunk did not end on a sentence boundary: [$chunk]", chunk.trim().endsWith("."))
        }
    }

    @Test
    fun neverCutsMidWordWhenNoSentencePunctuationExists() {
        val words = (1..300).map { "abcdefghij" }
        val text = words.joinToString(" ")
        val maxLength = 120

        val chunks = NarrationChunker.chunk(text, maxLength)
        val allowedWords = words.toSet()

        chunks.forEach { chunk ->
            assertTrue(chunk.length <= maxLength)
            chunk.split(" ").filter { it.isNotEmpty() }.forEach { fragment ->
                assertTrue("unexpected mid-word fragment: $fragment", fragment in allowedWords)
            }
        }
    }

    @Test
    fun concatenatingChunksReproducesOriginalTextModuloWhitespaceNormalization() {
        val text = """
            The artifact was donated in 1980.

            It was restored twice, most recently in 2015. The restoration used period-accurate materials.
        """.trimIndent()
        val maxLength = 60

        val chunks = NarrationChunker.chunk(text, maxLength)
        val recombined = chunks.joinToString(" ")

        assertEquals(normalizeWhitespace(text), normalizeWhitespace(recombined))
    }

    @Test
    fun aSingleWordLongerThanTheLimitIsHardSlicedWithoutExceedingIt() {
        val hugeWord = "a".repeat(300)
        val maxLength = 100

        val chunks = NarrationChunker.chunk(hugeWord, maxLength)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk -> assertTrue(chunk.length <= maxLength) }
        assertEquals(hugeWord, chunks.joinToString(""))
    }

    private fun normalizeWhitespace(value: String) = value.trim().replace(Regex("\\s+"), " ")
}
