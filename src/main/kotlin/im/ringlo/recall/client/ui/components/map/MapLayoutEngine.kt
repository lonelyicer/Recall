package im.ringlo.recall.client.ui.components.map

import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.hypot
import kotlin.math.pow
import net.minecraft.resources.Identifier

class MapLayoutEngine(map: ContentSnapshot.MapDefinition) {
    private val nodes = map.nodes.associate { it.id to MapNodeMotion(it) }
    private val ordered = nodes.values.toList()
    private val edges = map.edges.map { listOf(it.from, it.to).sorted() }.distinct()

    fun rect(node: ContentSnapshot.MapNode): UiRect =
        nodes[node.id]?.rect ?: UiRect(node.x + 18f, node.y + 1f, 28f, 28f)

    fun frame(seconds: Float, pinned: (Identifier) -> Boolean, obstacle: UiRect?) {
        var remaining = seconds.coerceIn(0f, 0.05f)
        while (remaining > 0f) {
            val dt = remaining.coerceAtMost(1f / 120f)
            step(dt, pinned, obstacle)
            remaining -= dt
        }
    }

    private fun step(dt: Float, pinned: (Identifier) -> Boolean, obstacle: UiRect?) {
        ordered.forEach { n ->
            n.forceX = (n.node.x - n.x) * 3.2f
            n.forceY = (n.node.y - n.y) * 3.2f
        }
        edges.forEach { edge ->
            val a = nodes[edge[0]] ?: return@forEach
            val b = nodes[edge[1]] ?: return@forEach
            if (!pinned(a.node.id) && !pinned(b.node.id)) pair(a, b, true)
        }
        ordered.forEachIndexed { index, a ->
            if (!pinned(a.node.id))
                for (j in index + 1 until ordered.size) {
                    val b = ordered[j]
                    if (!pinned(b.node.id)) pair(a, b, false)
                }
        }
        val damping = 0.84f.pow(dt * 20f)
        ordered.forEach { n ->
            if (pinned(n.node.id)) {
                n.velocityX = 0f
                n.velocityY = 0f
                return@forEach
            }
            if (obstacle != null) avoid(n, obstacle)
            n.velocityX = ((n.velocityX + n.forceX * dt) * damping).coerceIn(-260f, 260f)
            n.velocityY = ((n.velocityY + n.forceY * dt) * damping).coerceIn(-260f, 260f)
            n.x = (n.x + n.velocityX * dt).coerceIn(16f, 640f)
            n.y = (n.y + n.velocityY * dt).coerceIn(16f, 424f)
        }
    }

    private fun pair(a: MapNodeMotion, b: MapNodeMotion, edge: Boolean) {
        var dx = b.x - a.x
        val dy = b.y - a.y
        var distance = hypot(dx, dy)
        if (distance < 0.001f) {
            dx = 1f
            distance = 1f
        }
        val force =
            if (edge) (distance - 250f) * 1.1f
            else if (distance < 172f) -(1f - distance / 172f) * 90f else return
        val fx = dx / distance * force
        val fy = dy / distance * force
        a.forceX += fx
        a.forceY += fy
        b.forceX -= fx
        b.forceY -= fy
    }

    private fun avoid(n: MapNodeMotion, rect: UiRect) {
        val x = n.x + 32f
        val y = n.y + 30f
        var dx = x - x.coerceIn(rect.left, rect.right)
        var dy = y - y.coerceIn(rect.top, rect.bottom)
        var distance = hypot(dx, dy)
        if (distance < 0.001f) {
            val sides = listOf(x - rect.left, rect.right - x, y - rect.top, rect.bottom - y)
            when (sides.indexOf(sides.min())) {
                0 -> dx = -1f
                1 -> dx = 1f
                2 -> dy = -1f
                else -> dy = 1f
            }
            distance = 1f
        }
        if (distance >= 200f) return
        val force = (1f - distance / 200f) * 210f
        n.forceX += dx / distance * force
        n.forceY += dy / distance * force
    }
}
