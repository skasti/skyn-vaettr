package no.skasti.skynvaettr.reporting

/**
 * Graphviz attributes used when rendering a topology.
 *
 * Values are written as Graphviz attribute values and are escaped by [TopologyRenderer]. The
 * group label is supplied by the topology group itself and therefore should not be included in
 * [groupAttributes].
 */
data class TopologyGraphStyle(
    val graphAttributes: Map<String, String> = mapOf(
        "rankdir" to "LR",
        "bgcolor" to "transparent",
        "pad" to "0.2",
        "nodesep" to "0.5",
        "ranksep" to "0.8",
        "splines" to "ortho",
    ),
    val nodeAttributes: Map<String, String> = mapOf(
        "shape" to "box",
        "style" to "rounded,filled",
        "fillcolor" to "#E8F0FE",
        "color" to "#5B6B8C",
        "fontname" to "Arial",
    ),
    val edgeAttributes: Map<String, String> = mapOf(
        "color" to "#6B7280",
        "penwidth" to "1.2",
        "arrowsize" to "0.8",
    ),
    val groupAttributes: Map<String, String> = mapOf(
        "style" to "rounded,filled",
        "color" to "#8FAADC",
        "fillcolor" to "#F4F7FC",
        "labeljust" to "l",
    ),
)
