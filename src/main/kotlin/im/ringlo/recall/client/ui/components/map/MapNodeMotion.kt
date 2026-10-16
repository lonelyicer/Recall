package im.ringlo.recall.client.ui.components.map

import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.content.ContentSnapshot

class MapNodeMotion(val node: ContentSnapshot.MapNode) {
    var x = node.x
    var y = node.y
    var velocityX = 0f
    var velocityY = 0f
    var forceX = 0f
    var forceY = 0f
    val rect: UiRect
        get() = UiRect(x + 18f, y + 1f, 28f, 28f)
}
