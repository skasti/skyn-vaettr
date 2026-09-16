package no.skasti.skynvaettr.runtime

import kotlin.reflect.KClass
import no.skasti.skynvaettr.representation.Representation

/**
 * Converts newly available values of one type into a representation.
 *
 * [inputType] lets the runtime dispatch a heterogeneous list of entrypoints without weakening the
 * type of [process].
 */
interface EntryPoint<T : Any> {
    val inputType: KClass<T>

    fun process(items: List<T>): Representation
}
