package no.skasti.skynvaettr.runtime

/**
 * A processing component that owns ports and subscribes to their receive events.
 *
 * A node decides whether an incoming representation should be processed immediately, retained until
 * other ports have values, accumulated, or handled according to another domain-specific policy.
 */
interface Node {
    val ports: List<Port<*>>
}
