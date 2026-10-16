package im.ringlo.recall.content

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager

object ContentResources {
    fun load(
        resources: ResourceManager,
        overrides: ContentSnapshot = ContentSnapshot.empty(),
    ): ContentSnapshot {
        val articles =
            load(resources, "articles", ".md", overrides.articles) { id, resource ->
                resource.openAsReader().use { ContentCodec.decodeArticleResource(id, it) }
            }
        val guides =
            load(resources, "guides", ".json", overrides.guides) { id, resource ->
                resource.openAsReader().use { ContentCodec.decodeGuideResource(id, it) }
            }
        val maps =
            load(resources, "maps", ".json", overrides.maps) { id, resource ->
                resource.openAsReader().use { ContentCodec.decodeMapResource(id, it) }
            }
        val scenes =
            load(resources, "scenes", ".json", overrides.scenes) { id, resource ->
                val structure =
                    Identifier.fromNamespaceAndPath(id.namespace, "recall/scenes/${id.path}.nbt")
                resources.getResourceOrThrow(structure).open().use { nbt ->
                    resource.openAsReader().use { ContentCodec.decodeSceneResource(id, it, nbt) }
                }
            }
        return ContentCodec.validateSnapshot(
            ContentSnapshot(overrides.revision, guides, maps, scenes, articles)
        )
    }

    private fun <T> load(
        resources: ResourceManager,
        kind: String,
        suffix: String,
        overrides: Map<Identifier, T>,
        decode: (Identifier, Resource) -> T,
    ): Map<Identifier, T> {
        val prefix = "recall/$kind/"
        val result = linkedMapOf<Identifier, T>()
        resources
            .listResources(prefix.removeSuffix("/")) { it.path.endsWith(suffix) }
            .toSortedMap()
            .forEach { (resourceId, resource) ->
                val id =
                    Identifier.fromNamespaceAndPath(
                        resourceId.namespace,
                        resourceId.path.removePrefix(prefix).removeSuffix(suffix),
                    )
                if (id !in overrides) {
                    try {
                        check(result.put(id, decode(id, resource)) == null) {
                            "Duplicate Recall $kind resource $id"
                        }
                    } catch (exception: Exception) {
                        throw IllegalArgumentException(
                            "Could not load Recall resource $resourceId from ${resource.sourcePackId()}",
                            exception,
                        )
                    }
                }
            }
        result.putAll(overrides)
        return result
    }
}
