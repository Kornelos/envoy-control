package pl.allegro.tech.servicemesh.envoycontrol.consul.services

import pl.allegro.tech.servicemesh.envoycontrol.logger
import pl.allegro.tech.servicemesh.envoycontrol.services.ServiceInstance
import kotlin.text.Regex.Companion.escape

typealias ConsulServiceInstance = pl.allegro.tech.discovery.consul.recipes.watch.catalog.ServiceInstance

class ConsulServiceMapper(
    private val canaryTag: String = "",
    weightTag: String = "",
    private val defaultWeight: Int = 1
) {
    private val logger by logger()

    private val weightEnabled = !weightTag.isEmpty()
    private val weightTagRegex: Regex = weightTag.let { """${escape(it)}:(\d+)""".toRegex() }

    fun toDomainInstance(consulInstance: ConsulServiceInstance): ServiceInstance = ServiceInstance(
        id = consulInstance.serviceId,
        tags = consulInstance.serviceTags.toSet(),
        address = consulInstance.serviceAddress,
        port = consulInstance.servicePort,
        canary = isCanary(consulInstance),
        weight = getWeight(consulInstance)
    )

    private fun isCanary(consulInstance: ConsulServiceInstance): Boolean = when {
        canaryTag.isEmpty() -> false
        else -> consulInstance.serviceTags.orEmpty().contains(canaryTag)
    }

    private fun getWeight(consulInstance: ConsulServiceInstance): Int {
        if (!weightEnabled) {
            return defaultWeight
        }
        return consulInstance.serviceTags.orEmpty()
            .mapNotNull { weightTagRegex.matchEntire(it) }
            .map { it.groupValues[1] }
            .mapNotNull { it.runCatching { toInt() }.getOrNull() }
            .let { when (it.size) {
                0 -> defaultWeight
                else -> {
                    if (it.size > 1) {
                        logger.warn("Multiple weight tags on consul instance ${consulInstance.serviceId}. " +
                            "Expected 0 or 1.")
                    }
                    it[0].coerceAtLeast(1)
                }
            } }
    }
}