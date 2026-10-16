package im.ringlo.recall.api.data

import com.google.common.hash.HashCode
import com.google.gson.JsonObject
import im.ringlo.recall.content.ContentCodec
import im.ringlo.recall.content.ContentResources
import im.ringlo.recall.content.ContentSnapshot
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Provider-created content collector for one [RecallContentProvider.build] invocation.
 *
 * Guides, maps, articles, and scenes are written to the `guides`, `maps`, `articles`, and `scenes`
 * directories under `data/<namespace>/recall/`. IDs must be unique within each content kind in this
 * collector; different kinds may share an ID. Except for [id], submission methods preserve the
 * namespace of the supplied ID.
 *
 * Individual resource formats are checked on submission. After the provider finishes building, the
 * entire content graph is validated together with existing resources before files are written. This
 * is not a runtime content registry and must not be modified concurrently.
 */
class RecallDataOutput
internal constructor(
    private val namespace: String,
    private val resources: ResourceManager,
) {
    private val guides = linkedMapOf<Identifier, ContentSnapshot.Guide>()
    private val maps = linkedMapOf<Identifier, ContentSnapshot.MapDefinition>()
    private val articles = linkedMapOf<Identifier, ContentSnapshot.Article>()
    private val scenes = linkedMapOf<Identifier, ContentSnapshot.SceneDefinition>()
    private val json = linkedMapOf<Identifier, JsonObject>()
    private val markdown = linkedMapOf<Identifier, String>()

    /**
     * Creates a content ID in the provider's default namespace, allowing `/`-separated
     * subdirectories.
     *
     * @param path path without a namespace or file extension, such as `chapter_1/intro`.
     * @return an ID in the default namespace; no content is submitted by this method.
     * @throws IllegalArgumentException if the path contains empty, `.`, or `..` segments. Minecraft
     *   also rejects invalid identifier characters.
     */
    fun id(path: String): Identifier =
        Identifier.fromNamespaceAndPath(namespace, path).also(::validateId)

    /**
     * Submits a guide for generation as `recall/guides/<path>.json`.
     *
     * The `maps` list determines map order within the guide. Referenced maps may be submitted later
     * but must exist during full validation. Each map must belong to exactly one guide.
     *
     * @param guide guide definition whose ID determines the output namespace and path.
     * @throws IllegalArgumentException if this collector already has that guide ID or the path,
     *   fields, or counts violate resource format constraints.
     */
    fun guide(guide: ContentSnapshot.Guide) {
        val resource = resourceId("guides", guide.id, ".json")
        require(guide.id !in guides) { "Duplicate generated Recall guide ${guide.id}" }
        val value = ContentCodec.encodeGuideResource(guide)
        guides[guide.id] =
            StringReader(value.toString()).use { ContentCodec.decodeGuideResource(guide.id, it) }
        json[resource] = value
    }

    /**
     * Submits a map for generation as `recall/maps/<path>.json`.
     *
     * Exactly one guide must include the map. Node IDs must be unique within the map, and edges and
     * the starting node must reference valid nodes in that map. These references are checked during
     * full validation. Nodes may reference articles generated in this run or supplied as existing
     * resources.
     *
     * @param map map definition whose ID determines the output namespace and path.
     * @throws IllegalArgumentException if this collector already has that map ID or the path,
     *   fields, or counts violate resource format constraints.
     */
    fun map(map: ContentSnapshot.MapDefinition) {
        val resource = resourceId("maps", map.id, ".json")
        require(map.id !in maps) { "Duplicate generated Recall map ${map.id}" }
        val value = ContentCodec.encodeMapResource(map)
        maps[map.id] =
            StringReader(value.toString()).use { ContentCodec.decodeMapResource(map.id, it) }
        json[resource] = value
    }

    /**
     * Submits an article as UTF-8 `recall/articles/<path>.md` with Recall front matter.
     *
     * Metadata must round-trip losslessly through the existing Markdown parser. For example, titles
     * and summaries must be single-line, and tags cannot contain separator commas. Unsupported
     * values are rejected rather than silently rewritten. Scene references in the body are resolved
     * during full validation and may target scenes submitted later in this run.
     *
     * @param article article definition containing metadata and a Markdown body.
     * @throws IllegalArgumentException if the ID is duplicated, the path is invalid, the article is
     *   too large, or its metadata cannot be represented losslessly.
     */
    fun article(article: ContentSnapshot.Article) {
        val resource = resourceId("articles", article.id, ".md")
        require(article.id !in articles) { "Duplicate generated Recall article ${article.id}" }
        val text = ContentCodec.encodeArticleResource(article)
        articles[article.id] = ContentCodec.decodeArticleMarkdown(article.id, text)
        markdown[resource] = text
    }

    /**
     * Parses Markdown and regenerates it using [article]'s standard front matter format rather than
     * copying the input verbatim.
     *
     * Input may contain Recall front matter. If no title is supplied, the ID path becomes the
     * title.
     *
     * @param id article resource ID; a front matter `id`, if present, must match this value.
     * @param markdown complete Markdown document with optional front matter.
     * @throws IllegalArgumentException if the ID is duplicated, the path is invalid, or the content
     *   violates article encoding or length constraints.
     * @throws IllegalStateException if the front matter ID differs from [id].
     */
    fun article(id: Identifier, markdown: String) =
        article(ContentCodec.decodeArticleMarkdown(id, markdown))

    /**
     * Creates a timeline builder and submits scene JSON without generating structure NBT or
     * executing client instructions.
     *
     * Like the overload accepting [SceneTimelineBuilder], submission immediately reads and
     * validates the matching NBT, which must already exist in the provider's resource manager.
     *
     * @param id scene ID determining the namespace and path of both the timeline and structure NBT.
     * @param zeroLayer NBT layer treated as scene `y = 0`; must be in `0..127` and below the
     *   structure height.
     * @param timeline callback building the action list with times expressed in seconds.
     * @throws IllegalArgumentException if the ID is duplicated or the structure, parameters, times,
     *   or references are invalid.
     * @throws IllegalStateException if the timeline uses an unregistered action.
     */
    fun scene(
        id: Identifier,
        zeroLayer: Int = 0,
        timeline: SceneTimelineBuilder.() -> Unit,
    ) = scene(id, SceneTimelineBuilder(zeroLayer).apply(timeline))

    /**
     * Submits a snapshot of the builder's current content as `recall/scenes/<path>.json`.
     *
     * Immediately reads `data/<namespace>/recall/scenes/<path>.nbt` from existing resources and
     * uses the runtime decoder to validate the structure, groups, timeline, and references. Missing
     * or unreadable NBT causes failure. Later builder changes do not affect the submitted scene.
     * Loader-added default durations and overlay fade times are not written back into the resource.
     *
     * @param id scene resource ID, such as `example:intro`; file extensions are appended
     *   automatically.
     * @param timeline timeline builder to submit.
     * @throws IllegalArgumentException if the ID is duplicated, the path is invalid, or the scene
     *   violates structure, timing, parameter, or reference constraints.
     * @throws IllegalStateException if the timeline uses an unregistered action.
     */
    fun scene(id: Identifier, timeline: SceneTimelineBuilder) {
        val resource = resourceId("scenes", id, ".json")
        require(id !in scenes) { "Duplicate generated Recall scene $id" }
        val structure = resourceId("scenes", id, ".nbt")
        val value = timeline.build()
        val definition =
            resources.getResourceOrThrow(structure).open().use { nbt ->
                StringReader(value.toString()).use { reader ->
                    ContentCodec.decodeSceneResource(id, reader, nbt)
                }
            }
        scenes[id] = definition
        json[resource] = value
    }

    internal fun validate() {
        val content = ContentSnapshot(0L, guides, maps, scenes, articles)
        ContentResources.load(resources, content)
    }

    internal fun save(output: PackOutput, cache: CachedOutput): CompletableFuture<*> {
        val root = output.getOutputFolder(PackOutput.Target.DATA_PACK)
        val tasks = mutableListOf<CompletableFuture<*>>()
        json.toSortedMap().forEach { (id, value) ->
            tasks += DataProvider.saveStable(cache, value, id.resolveAgainst(root))
        }
        markdown.toSortedMap().forEach { (id, text) ->
            tasks += CompletableFuture.runAsync {
                val bytes = text.toByteArray(StandardCharsets.UTF_8)
                val hash = HashCode.fromBytes(MessageDigest.getInstance("SHA-1").digest(bytes))
                cache.writeIfNeeded(id.resolveAgainst(root), bytes, hash)
            }
        }
        return CompletableFuture.allOf(*tasks.toTypedArray())
    }

    private fun resourceId(kind: String, id: Identifier, suffix: String): Identifier {
        validateId(id)
        return Identifier.fromNamespaceAndPath(id.namespace, "recall/$kind/${id.path}$suffix")
    }

    private fun validateId(id: Identifier) {
        require(id.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid Recall content path $id"
        }
    }
}
