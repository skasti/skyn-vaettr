package no.skasti.skynvaettr.topology

/** Validates the intrinsic invariants of a topology's named groups. */
fun Map<String, Group>.validate() {
    forEach { (name, group) ->
        require(name.isNotBlank()) {
            "topology group name must not be blank"
        }
        require(name == group.name) {
            "topology group key '$name' does not match group name '${group.name}'"
        }
        require(group.nodes.isNotEmpty()) {
            "topology group '$name' must contain nodes"
        }
    }
}
