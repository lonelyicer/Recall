package im.ringlo.recall.content

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import im.ringlo.recall.content.scene.SceneCodec
import java.io.InputStream
import java.io.Reader
import net.minecraft.resources.Identifier

object ContentCodec {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private const val MAX_TEXT = 256_000
    private const val MAX_SNAPSHOT = 4_000_000
    private const val MAX_ARTICLES = 4_096
    private const val MAX_MAPS_PER_GUIDE = 256
    private const val MAX_NODES_PER_MAP = 512
    private const val MAX_COORDINATE = 100_000f

    fun decodeSnapshot(json: String): ContentSnapshot {
        require(json.length <= MAX_SNAPSHOT) { "Recall content exceeds the transfer limit" }
        val root = JsonParser.parseString(json).asJsonObject
        val schema = root.get("schema_version")?.asInt ?: ContentSnapshot.SCHEMA_VERSION
        require(schema == ContentSnapshot.SCHEMA_VERSION) {
            "Unsupported Recall content schema: $schema"
        }
        val revision = root.get("revision")?.asLong ?: 0L
        val guides = linkedMapOf<Identifier, ContentSnapshot.Guide>()
        root.getAsJsonArray("guides")?.forEach { element ->
            val value = element.asJsonObject
            val id = parseId(required(value, "id"), "guide id")
            check(guides.put(id, decodeGuide(id, value)) == null) { "Duplicate Recall guide: $id" }
        }
        val maps = linkedMapOf<Identifier, ContentSnapshot.MapDefinition>()
        root.getAsJsonArray("maps")?.forEach { element ->
            val map = decodeMap(element.asJsonObject)
            check(maps.put(map.id, map) == null) { "Duplicate Recall map: ${map.id}" }
        }
        val scenes = linkedMapOf<Identifier, ContentSnapshot.SceneDefinition>()
        root.getAsJsonArray("scenes")?.forEach { element ->
            val scene = SceneCodec.decode(element.asJsonObject)
            check(scenes.put(scene.id, scene) == null) { "Duplicate Recall scene: ${scene.id}" }
        }
        val articles = linkedMapOf<Identifier, ContentSnapshot.Article>()
        root.getAsJsonArray("articles")?.forEach { element ->
            val article = decodeArticle(element.asJsonObject)
            check(articles.put(article.id, article) == null) {
                "Duplicate Recall article: ${article.id}"
            }
        }
        return validateSnapshot(ContentSnapshot(revision, guides, maps, scenes, articles))
    }

    fun decodeGuideResource(id: Identifier, reader: Reader): ContentSnapshot.Guide {
        val decoded = decodeGuide(id, JsonParser.parseReader(reader).asJsonObject)
        check(decoded.id == id) { "Recall guide id ${decoded.id} does not match resource id $id" }
        return decoded
    }

    fun decodeMapResource(id: Identifier, reader: Reader): ContentSnapshot.MapDefinition {
        val decoded = decodeMap(JsonParser.parseReader(reader).asJsonObject, id)
        check(decoded.id == id) { "Recall map id ${decoded.id} does not match resource id $id" }
        return decoded
    }

    fun decodeSceneResource(
        id: Identifier,
        reader: Reader,
        structure: InputStream,
    ): ContentSnapshot.SceneDefinition = SceneCodec.resource(id, reader, structure)

    fun decodeArticleResource(id: Identifier, reader: Reader): ContentSnapshot.Article =
        decodeArticleMarkdown(id, reader.readText())

    fun decodeArticleMarkdown(resourceId: Identifier, markdown: String): ContentSnapshot.Article {
        require(markdown.length <= MAX_TEXT) { "Article $resourceId is too large" }
        val (frontMatter, body) = splitFrontMatter(markdown)
        val declaredId = frontMatter["id"]?.let { parseId(it, "article id") }
        check(declaredId == null || declaredId == resourceId) {
            "Article id ${frontMatter["id"]} does not match resource id $resourceId"
        }
        val tags =
            frontMatter["tags"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        return ContentSnapshot.Article(
            id = resourceId,
            title = frontMatter["title"]?.ifBlank { null } ?: resourceId.path,
            summary = frontMatter["summary"].orEmpty(),
            body = body,
            tags = tags,
        )
    }

    fun encodeSnapshot(snapshot: ContentSnapshot): String {
        val root =
            JsonObject().apply {
                addProperty("schema_version", ContentSnapshot.SCHEMA_VERSION)
                addProperty("revision", snapshot.revision)
                add(
                    "guides",
                    JsonArray().also { values ->
                        snapshot.guides.values
                            .sortedBy { it.id }
                            .forEach { values.add(encodeGuideResource(it)) }
                    },
                )
                add(
                    "maps",
                    JsonArray().also { values ->
                        snapshot.maps.values
                            .sortedBy { it.id }
                            .forEach { values.add(encodeMapResource(it)) }
                    },
                )
                add(
                    "scenes",
                    JsonArray().also { values ->
                        snapshot.scenes.values
                            .sortedBy { it.id }
                            .forEach { values.add(SceneCodec.encode(it)) }
                    },
                )
                add(
                    "articles",
                    JsonArray().also { values ->
                        snapshot.articles.values
                            .sortedBy { it.id }
                            .forEach { values.add(encodeArticle(it)) }
                    },
                )
            }
        return gson.toJson(root).also {
            check(it.length <= MAX_SNAPSHOT) { "Recall content exceeds the transfer limit" }
        }
    }

    private fun decodeGuide(id: Identifier, value: JsonObject): ContentSnapshot.Guide {
        value.get("id")?.asString?.let { declared ->
            check(parseId(declared, "guide id") == id) {
                "Recall guide id $declared does not match resource id $id"
            }
        }
        val maps =
            (value.getAsJsonArray("maps") ?: JsonArray()).map { parseId(it.asString, "map id") }
        require(maps.size <= MAX_MAPS_PER_GUIDE) { "Guide $id has too many maps" }
        return ContentSnapshot.Guide(
            id = id,
            title = required(value, "title"),
            subtitle = value.get("subtitle")?.asString.orEmpty(),
            maps = maps,
            icon = value.get("icon")?.asString?.let { parseId(it, "guide icon item id") },
        )
    }

    private fun decodeArticle(value: JsonObject): ContentSnapshot.Article {
        val id = parseId(required(value, "id"), "article id")
        val body = value.get("body")?.asString ?: error("Snapshot article $id is missing body")
        require(body.length <= MAX_TEXT) { "Article $id is too large" }
        return ContentSnapshot.Article(
            id,
            value.get("title")?.asString ?: id.path,
            value.get("summary")?.asString.orEmpty(),
            body,
            value.getAsJsonArray("tags")?.map { it.asString } ?: emptyList(),
        )
    }

    private fun decodeMap(
        value: JsonObject,
        fallbackId: Identifier? = null,
    ): ContentSnapshot.MapDefinition {
        val id =
            parseId(
                value.get("id")?.asString
                    ?: checkNotNull(fallbackId) { "Missing map id" }.toString(),
                "map id",
            )
        val nodeArray = value.getAsJsonArray("nodes") ?: JsonArray()
        require(nodeArray.size() <= MAX_NODES_PER_MAP) { "Map $id has too many nodes" }
        val nodes = nodeArray.map { element ->
            val node = element.asJsonObject
            val articleIds =
                when {
                    node.has("articles") ->
                        node.getAsJsonArray("articles").map { parseId(it.asString, "article id") }
                    node.has("article") ->
                        listOf(parseId(node.get("article").asString, "article id"))
                    else -> emptyList()
                }
            ContentSnapshot.MapNode(
                id = parseId(required(node, "id"), "node id"),
                articleIds = articleIds,
                x = coordinate(node, "x"),
                y = coordinate(node, "y"),
                title = node.get("title")?.asString ?: node.get("id")?.asString ?: "node",
                summary = node.get("summary")?.asString.orEmpty(),
                item = node.get("item")?.asString?.let { parseId(it, "item id") },
            )
        }
        val edges =
            (value.getAsJsonArray("edges") ?: value.getAsJsonArray("connections") ?: JsonArray())
                .map { element ->
                    val edge = element.asJsonObject
                    ContentSnapshot.MapEdge(
                        parseId(required(edge, "from"), "edge source"),
                        parseId(required(edge, "to"), "edge target"),
                        edge.get("kind")?.asString ?: "related",
                    )
                }
        return ContentSnapshot.MapDefinition(
            id,
            value.get("title")?.asString ?: id.path,
            nodes,
            edges,
            value.get("start_node")?.asString?.let { parseId(it, "start node id") },
        )
    }

    fun validateSnapshot(snapshot: ContentSnapshot): ContentSnapshot {
        require(snapshot.articles.size <= MAX_ARTICLES) { "Recall has too many articles" }
        val guideMapIds = mutableSetOf<Identifier>()
        snapshot.guides.values.forEach { guide ->
            check(guide.maps.distinct().size == guide.maps.size) {
                "Guide ${guide.id} contains duplicate maps"
            }
            guide.maps.forEach { mapId ->
                check(snapshot.maps.containsKey(mapId)) {
                    "Guide ${guide.id} references missing map $mapId"
                }
                check(guideMapIds.add(mapId)) { "Map $mapId is referenced by multiple guides" }
            }
        }
        snapshot.maps.keys.forEach { mapId ->
            check(mapId in guideMapIds) { "Map $mapId is not indexed by a guide" }
        }
        snapshot.articles.values.forEach { article ->
            Regex("\\[\\[scene:([^]]+)]]").findAll(article.body).forEach { match ->
                val sceneId = parseId(match.groupValues[1], "scene id")
                check(snapshot.scenes.containsKey(sceneId)) {
                    "Article ${article.id} references missing scene $sceneId"
                }
            }
        }
        snapshot.maps.values.forEach { map ->
            map.startNode?.let { start ->
                check(map.nodes.any { it.id == start }) {
                    "Map ${map.id} references missing start node $start"
                }
            }
            val nodeIds = mutableSetOf<Identifier>()
            map.nodes.forEach { node ->
                check(nodeIds.add(node.id)) { "Duplicate node ${node.id} in map ${map.id}" }
                require(
                    node.x.isFinite() &&
                        node.y.isFinite() &&
                        kotlin.math.abs(node.x) <= MAX_COORDINATE &&
                        kotlin.math.abs(node.y) <= MAX_COORDINATE
                ) {
                    "Map ${map.id} has an invalid node coordinate"
                }
                node.articleIds.forEach { article ->
                    check(snapshot.articles.containsKey(article)) {
                        "Map ${map.id} references missing article $article"
                    }
                }
            }
            map.edges.forEach { edge ->
                check(
                    nodeIds.contains(edge.from) && nodeIds.contains(edge.to) && edge.from != edge.to
                ) {
                    "Map ${map.id} has an invalid edge"
                }
            }
        }
        snapshot.scenes.values.forEach(SceneCodec::validate)
        return snapshot
    }

    fun encodeGuideResource(guide: ContentSnapshot.Guide) =
        JsonObject().apply {
            addProperty("id", guide.id.toString())
            addProperty("title", guide.title)
            addProperty("subtitle", guide.subtitle)
            guide.icon?.let { addProperty("icon", it.toString()) }
            add(
                "maps",
                JsonArray().also { values -> guide.maps.forEach { values.add(it.toString()) } },
            )
        }

    fun encodeArticleResource(article: ContentSnapshot.Article): String {
        val markdown = buildString {
            appendLine("---")
            appendLine("id: ${article.id}")
            appendLine("title: ${article.title}")
            appendLine("summary: ${article.summary}")
            appendLine("tags: ${article.tags.joinToString(", ")}")
            appendLine("---")
            append(article.body)
        }
        require(decodeArticleMarkdown(article.id, markdown) == article) {
            "Article ${article.id} metadata cannot be represented in Recall Markdown front matter"
        }
        return markdown
    }

    private fun encodeArticle(article: ContentSnapshot.Article) =
        JsonObject().apply {
            addProperty("id", article.id.toString())
            addProperty("title", article.title)
            addProperty("summary", article.summary)
            addProperty("body", article.body)
            add("tags", JsonArray().also { values -> article.tags.forEach(values::add) })
        }

    fun encodeMapResource(map: ContentSnapshot.MapDefinition) =
        JsonObject().apply {
            addProperty("id", map.id.toString())
            addProperty("title", map.title)
            map.startNode?.let { addProperty("start_node", it.toString()) }
            add(
                "nodes",
                JsonArray().also { values ->
                    map.nodes.forEach { node ->
                        values.add(
                            JsonObject().apply {
                                addProperty("id", node.id.toString())
                                add(
                                    "articles",
                                    JsonArray().also { ids ->
                                        node.articleIds.forEach { ids.add(it.toString()) }
                                    },
                                )
                                addProperty("x", node.x)
                                addProperty("y", node.y)
                                addProperty("title", node.title)
                                addProperty("summary", node.summary)
                                node.item?.let { addProperty("item", it.toString()) }
                            }
                        )
                    }
                },
            )
            add(
                "edges",
                JsonArray().also { values ->
                    map.edges.forEach { edge ->
                        values.add(
                            JsonObject().apply {
                                addProperty("from", edge.from.toString())
                                addProperty("to", edge.to.toString())
                                addProperty("kind", edge.kind)
                            }
                        )
                    }
                },
            )
        }

    private fun splitFrontMatter(markdown: String): Pair<Map<String, String>, String> {
        if (!markdown.startsWith("---")) return emptyMap<String, String>() to markdown
        val firstLineEnd = markdown.indexOf('\n')
        if (firstLineEnd < 0) return emptyMap<String, String>() to markdown
        val end = markdown.indexOf("\n---", firstLineEnd + 1)
        if (end < 0) return emptyMap<String, String>() to markdown
        val fields =
            markdown
                .substring(firstLineEnd + 1, end)
                .lineSequence()
                .mapNotNull { line ->
                    val colon = line.indexOf(':')
                    if (colon <= 0) null
                    else
                        line.substring(0, colon).trim().lowercase() to
                            line.substring(colon + 1).trim().trim('"')
                }
                .toMap()
        return fields to markdown.substring(end + 4).removePrefix("\n")
    }

    private fun coordinate(value: JsonObject, key: String): Float = value.get(key)?.asFloat ?: 0f

    private fun required(value: JsonObject, field: String): String =
        checkNotNull(value.get(field)) { "Missing Recall field: $field" }.asString

    private fun parseId(value: String, field: String): Identifier =
        Identifier.tryParse(value) ?: error("Invalid $field: $value")
}
