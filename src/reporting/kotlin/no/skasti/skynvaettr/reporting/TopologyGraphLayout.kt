package no.skasti.skynvaettr.reporting

import no.skasti.skynvaettr.topology.Node

/** Optional Graphviz rank and feedback hints for topologies with branches or learning loops. */
data class TopologyGraphLayout(
    /** Nodes in each group are placed in the same left-to-right rank. */
    val sameRank: List<List<Node>> = emptyList(),
    /** Nodes placed at the final left-to-right rank. */
    val sink: List<Node> = emptyList(),
    /** Directed edges that remain visible but do not affect rank assignment. */
    val unconstrainedEdges: List<Pair<Node, Node>> = emptyList(),
)
