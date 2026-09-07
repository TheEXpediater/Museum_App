package com.example.museumapp.narration

/**
 * Splits narration text into chunks that stay under TextToSpeech's speech-input limit
 * (`TextToSpeech.getMaxSpeechInputLength()`, ~4000 chars), preferring to break on paragraph then
 * sentence boundaries, and only falling back to word boundaries when a single sentence alone
 * exceeds the limit -- chunks never cut mid-word. Joining the returned chunks with a single space
 * reproduces the original text, modulo the paragraph/sentence whitespace this chunker collapses
 * to single spaces.
 */
object NarrationChunker {
    private val PARAGRAPH_BOUNDARY = Regex("\\n\\s*\\n")
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
    private val WORD_BOUNDARY = Regex("\\s+")

    fun chunk(text: String, maxLength: Int): List<String> {
        require(maxLength > 0) { "maxLength must be positive." }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxLength) return listOf(trimmed)

        val paragraphs = trimmed.split(PARAGRAPH_BOUNDARY).map { it.trim() }.filter { it.isNotEmpty() }
        val units = paragraphs.flatMap { paragraph ->
            if (paragraph.length <= maxLength) listOf(paragraph) else splitBySentence(paragraph, maxLength)
        }
        return pack(units, maxLength)
    }

    private fun splitBySentence(paragraph: String, maxLength: Int): List<String> {
        val sentences = paragraph.split(SENTENCE_BOUNDARY).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.flatMap { sentence ->
            if (sentence.length <= maxLength) listOf(sentence) else splitByWord(sentence, maxLength)
        }
    }

    private fun splitByWord(text: String, maxLength: Int): List<String> {
        val words = text.split(WORD_BOUNDARY).filter { it.isNotEmpty() }
        return pack(words, maxLength)
    }

    /** Greedily packs [units] into as-large-as-possible chunks, hard-slicing a lone unit that by itself exceeds [maxLength] (e.g. one very long word). */
    private fun pack(units: List<String>, maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        units.forEach { unit ->
            val addition = if (current.isEmpty()) unit else " $unit"
            if (current.length + addition.length > maxLength) {
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current.clear()
                }
                if (unit.length > maxLength) {
                    chunks += unit.chunked(maxLength)
                } else {
                    current.append(unit)
                }
            } else {
                current.append(addition)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
