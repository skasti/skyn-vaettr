package no.skasti.skynvaettr.tokenization

import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.signals.SignalId

class SignalTokenizationTest {
    private val tokenizer = DelimitedSignalTokenizer()

    @Test
    fun `tokenizes hierarchical and compound signal names`() {
        assertEquals(
            listOf("sensor", "kontor", "presence", "temperature"),
            tokenizer.tokenize(SignalId("sensor.kontor_presence_temperature")).map { it.value },
        )
    }

    @Test
    fun `builds deterministic vocabulary from token frequency`() {
        val vocabulary =
            VocabularyBuilder()
                .add(tokenizer.tokenize(SignalId("sensor.indoor.temperature")))
                .add(tokenizer.tokenize(SignalId("sensor.outdoor.temperature")))
                .build()

        assertEquals(
            listOf("<PAD>", "<UNK>", "<MASK>", "sensor", "temperature", "indoor", "outdoor"),
            vocabulary.tokens.map { it.value },
        )
    }

    @Test
    fun `unknown tokens resolve to unknown id`() {
        val vocabulary =
            VocabularyBuilder()
                .add(tokenizer.tokenize(SignalId("sensor.indoor.temperature")))
                .build()

        assertEquals(
            vocabulary.idOf(ReservedTokens.UNK),
            vocabulary.idOf(Token("unseen")),
        )
    }
}
