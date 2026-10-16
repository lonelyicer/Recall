package im.ringlo.recall.content

import com.google.gson.JsonObject
import im.ringlo.recall.api.scene.SceneArguments
import net.minecraft.resources.Identifier

data class ContentSnapshot(
    val revision: Long,
    val guides: Map<Identifier, Guide>,
    val maps: Map<Identifier, MapDefinition> = emptyMap(),
    val scenes: Map<Identifier, SceneDefinition> = emptyMap(),
    val articles: Map<Identifier, Article> = emptyMap(),
) {
    data class Guide(
        val id: Identifier,
        val title: String,
        val subtitle: String,
        val maps: List<Identifier>,
        val icon: Identifier? = null,
    )

    data class Article(
        val id: Identifier,
        val title: String,
        val summary: String,
        val body: String,
        val tags: List<String> = emptyList(),
    )

    data class MapDefinition(
        val id: Identifier,
        val title: String,
        val nodes: List<MapNode>,
        val edges: List<MapEdge>,
        val startNode: Identifier? = null,
    )

    data class MapNode(
        val id: Identifier,
        val articleIds: List<Identifier>,
        val x: Float,
        val y: Float,
        val title: String = id.path,
        val summary: String = "",
        val item: Identifier? = null,
    ) {
        constructor(
            id: Identifier,
            articleId: Identifier,
            x: Float,
            y: Float,
        ) : this(
            id = id,
            articleIds = listOf(articleId),
            x = x,
            y = y,
        )
    }

    data class MapEdge(val from: Identifier, val to: Identifier, val kind: String = "related")

    data class SceneDefinition(
        val id: Identifier,
        val title: String,
        val duration: Float,
        val steps: List<SceneStep>,
        val blocks: List<SceneBlock> = emptyList(),
        val entities: List<String> = emptyList(),
        val size: List<Int> = listOf(1, 1, 1),
        val zeroLayer: Int = 0,
    )

    data class SceneBlock(
        val x: Int,
        val y: Int,
        val z: Int,
        val state: String,
        val group: String = "main",
        val nbt: String? = null,
    )

    data class SceneStep(
        val time: Float,
        val type: Identifier,
        val duration: Float = 0f,
        val parameters: JsonObject = JsonObject(),
    ) {
        val group: String
            get() = parameters.get("group")?.asString ?: "main"

        val action: String
            get() = type.path

        val x: Float
            get() = SceneArguments.vector(parameters, "offset").x.toFloat()

        val y: Float
            get() = SceneArguments.vector(parameters, "offset").y.toFloat()

        val z: Float
            get() = SceneArguments.vector(parameters, "offset").z.toFloat()
    }

    companion object {
        const val SCHEMA_VERSION = 2

        fun empty() = ContentSnapshot(0L, emptyMap())
    }
}
