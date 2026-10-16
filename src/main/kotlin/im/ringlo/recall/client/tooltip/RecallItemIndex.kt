package im.ringlo.recall.client.tooltip

import im.ringlo.recall.content.ClientContentStore
import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.resources.Identifier

object RecallItemIndex {
    private var indexed: ContentSnapshot? = null
    private var entries =
        emptyMap<Identifier, Pair<ContentSnapshot.MapDefinition, ContentSnapshot.MapNode>>()

    @Synchronized
    fun find(item: Identifier): Pair<ContentSnapshot.MapDefinition, ContentSnapshot.MapNode>? {
        val snapshot = ClientContentStore.snapshot()
        if (snapshot !== indexed) {
            entries = buildMap {
                snapshot.guides.values.forEach { guide ->
                    guide.maps.mapNotNull(snapshot.maps::get).forEach { map ->
                        map.nodes.forEach { node ->
                            val id = node.item
                            if (id != null && node.articleIds.any(snapshot.articles::containsKey)) {
                                putIfAbsent(id, map to node)
                            }
                        }
                    }
                }
            }
            indexed = snapshot
        }
        return entries[item]
    }
}
