package no.skasti.skynvaettr.topology

import kotlin.reflect.KClass

/**
 * Converts newly available values of one type into one or more representations.
 *
 * Implementations expose their output ports and emit representations through those ports from
 * [process]. This keeps entrypoints compatible with the same topology as other nodes.
 *
 * [inputType] lets the runtime dispatch a heterogeneous list of entrypoints without weakening the
 * type of [process]. It must identify a specific input type; broad catch-all types such as `Any`
 * are not valid topology entrypoints. Distinct entrypoint types may overlap; the environment
 * includes a matching value in every corresponding batch.
 */
interface EntryPoint<T : Any>: Node {
    val inputType: KClass<T>

    fun process(items: List<T>)
}
