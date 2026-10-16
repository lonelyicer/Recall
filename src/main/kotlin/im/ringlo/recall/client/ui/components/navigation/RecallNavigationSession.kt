package im.ringlo.recall.client.ui.components.navigation

import im.ringlo.recall.content.ContentSnapshot
import net.minecraft.resources.Identifier

class RecallNavigationSession {
    var guideId: Identifier? = null
        private set

    var mapId: Identifier? = null
        private set

    var selectedNodeId: Identifier? = null
        private set

    var selectedArticleId: Identifier? = null
        private set

    var articleScroll = 0f

    fun install(snapshot: ContentSnapshot) {
        var guide = guideId?.let(snapshot.guides::get)
        if (guide == null) {
            guide = snapshot.guides.values.firstOrNull()
            guideId = guide?.id
        }
        val previousMapId = mapId
        val validMap =
            mapId?.let { id -> id in guide?.maps.orEmpty() && id in snapshot.maps } == true
        if (!validMap) mapId = guide?.maps?.firstOrNull { it in snapshot.maps }
        if (mapId != previousMapId) clearNode()
        val node = currentNode(snapshot)
        if (node == null) clearNode()
        if (selectedArticleId !in node?.articleIds.orEmpty()) selectedArticleId = null
    }

    fun selectGuide(guide: ContentSnapshot.Guide) {
        guideId = guide.id
        mapId = guide.maps.firstOrNull()
        clearNode()
    }

    fun selectMap(map: ContentSnapshot.MapDefinition, snapshot: ContentSnapshot) {
        if (map.id !in snapshot.maps) return
        val owner = snapshot.guides.values.firstOrNull { map.id in it.maps } ?: return
        mapId = map.id
        guideId = owner.id
        clearNode()
    }

    fun selectNode(node: ContentSnapshot.MapNode) {
        selectedNodeId = node.id
        selectedArticleId = null
        articleScroll = 0f
    }

    fun selectArticle(article: ContentSnapshot.Article) {
        selectedArticleId = article.id
    }

    fun toggleArticle(article: ContentSnapshot.Article) {
        selectedArticleId = if (selectedArticleId == article.id) null else article.id
    }

    fun collapseArticle() {
        selectedArticleId = null
    }

    fun clearNode() {
        selectedNodeId = null
        selectedArticleId = null
        articleScroll = 0f
    }

    fun currentGuide(snapshot: ContentSnapshot): ContentSnapshot.Guide? =
        guideId?.let(snapshot.guides::get)

    fun currentMap(snapshot: ContentSnapshot): ContentSnapshot.MapDefinition? =
        mapId?.let(snapshot.maps::get)

    fun currentNode(snapshot: ContentSnapshot): ContentSnapshot.MapNode? =
        currentMap(snapshot)?.nodes?.firstOrNull { it.id == selectedNodeId }
}
