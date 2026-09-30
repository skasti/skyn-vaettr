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
        "bgcolor" to "white",
        "pad" to "0.2",
        "nodesep" to "0.6",
        "ranksep" to "0.9",
        "splines" to "ortho",
        "outputorder" to "edgesfirst",
        "concentrate" to "false",
        "forcelabels" to "true",
        "newrank" to "true",
    ),
    val nodeAttributes: Map<String, String> = mapOf(
        "shape" to "box",
        "style" to "rounded,filled",
        "fillcolor" to "#E4F3FF",
        "color" to "#CFDCE8",
        "penwidth" to "1.4",
        "fontname" to "Arial",
        "fontsize" to "16",
        "fontcolor" to "#0055A5",
        "margin" to "0.22,0.16",
    ),
    val edgeAttributes: Map<String, String> = mapOf(
        "color" to "#888888",
        "penwidth" to "1.1",
        "arrowsize" to "0.7",
        "fontname" to "Arial",
        "fontsize" to "10",
        "fontcolor" to "#0055A5",
    ),
    val groupAttributes: Map<String, String> = mapOf(
        "style" to "rounded",
        "color" to "#C7D8E8",
        "fontcolor" to "#0055A5",
        "fontname" to "Arial",
        "labeljust" to "l",
    ),
)
