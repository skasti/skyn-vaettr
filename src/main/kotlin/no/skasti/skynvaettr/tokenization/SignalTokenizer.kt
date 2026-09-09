package no.skasti.skynvaettr.tokenization

import java.util.Locale
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

fun interface SignalTokenizer {
    fun tokenize(signalId: SignalId): List<Token>

    fun tokenize(signal: Signal<*>): List<Token> = tokenize(signal.id)
}

/**
 * Simple source-agnostic baseline tokenizer for hierarchical signal names.
 */
class DelimitedSignalTokenizer(
    private val delimiters: Regex = Regex("""[./:_\-]+"""),
    private val lowercase: Boolean = true,
) : SignalTokenizer {
    override fun tokenize(signalId: SignalId): List<Token> =
        signalId.value
            .split(delimiters)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (lowercase) it.lowercase(Locale.ROOT) else it }
            .map(::Token)
            .toList()
}
