package no.skasti.skynvaettr.environment

import kotlin.reflect.KClass

/** Simple process-local environment suitable for experiments and tests. */
open class InMemoryEnvironment : Environment {
    private val items = mutableListOf<Any>()
    private val consumedCounts = mutableMapOf<KClass<*>, Int>()

    fun append(items: List<*>) {
        items.forEach { item ->
            requireNotNull(item) { "environment items must not be null" }
            this.items += item
        }
    }

    fun append(item: Any) = append(listOf(item))

    override fun getNew(types: List<KClass<*>>): Map<KClass<*>, List<Any>> =
        types.distinct().associateWith { type ->
            val objectType = type.javaObjectType
            val matching = items.filter { objectType.isInstance(it) }
            val consumed = consumedCounts[type] ?: 0
            require(consumed <= matching.size) {
                "environment items cannot be removed while consumption is tracked"
            }
            consumedCounts[type] = matching.size
            matching.drop(consumed)
        }
}
