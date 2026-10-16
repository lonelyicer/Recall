package im.ringlo.recall.api.data

import java.util.concurrent.CompletableFuture
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager

/**
 * NeoForge [DataProvider] base class for generating Recall data pack content.
 *
 * Register subclasses through `GatherDataEvent.Server` and submit guides, maps, articles, and scene
 * timelines in [build]. Generation neither executes client instructions nor creates structure NBT.
 * Each scene's matching NBT must be available through [resources].
 *
 * Files are written only after all content from this provider has been built and validated by the
 * shared loader. Keep mutually referencing generated resources in one provider: unwritten output
 * from other providers is not included automatically. Visible hand-authored and dependency
 * resources participate in validation; generated entries override existing entries of the same kind
 * and ID.
 *
 * @param output output directory configuration supplied by NeoForge.
 * @param namespace default content namespace; must be a valid Minecraft namespace.
 * @param resources server data resource manager providing existing content and structure NBT,
 *   typically obtained from the Datagen event for `SERVER_DATA`.
 */
abstract class RecallContentProvider(
    private val output: PackOutput,
    private val namespace: String,
    private val resources: ResourceManager,
) : DataProvider {
    init {
        Identifier.fromNamespaceAndPath(namespace, "recall")
    }

    /**
     * Submits content to this run's collector instead of writing files directly.
     *
     * Cross-resource references between guides, maps, and articles are checked after this method
     * returns, so forward references are allowed. Submitting a scene immediately reads its matching
     * structure NBT. Each [run] creates a new collector; do not retain [content] between runs.
     *
     * @param content output collector exclusive to this provider run.
     */
    protected abstract fun build(content: RecallDataOutput)

    /**
     * Runs generation in build, full validation, and cached write order.
     *
     * Build or validation failures throw synchronously before a future is returned and before any
     * files from this provider are written. Writes are not transactional: an I/O failure does not
     * guarantee rollback.
     *
     * @param cache NeoForge's incremental output cache.
     * @return a future covering all output write tasks.
     */
    final override fun run(cache: CachedOutput): CompletableFuture<*> {
        val content = RecallDataOutput(namespace, resources)
        build(content)
        content.validate()
        return content.save(output, cache)
    }

    /**
     * Returns the provider name used in Datagen logs and cache identification.
     *
     * Override this method with unique names when registering multiple providers in one namespace.
     *
     * @return the default name, `<namespace>: Recall content`.
     */
    override fun getName(): String = "$namespace: Recall content"
}
