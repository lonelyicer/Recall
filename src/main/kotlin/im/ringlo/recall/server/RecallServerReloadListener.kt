package im.ringlo.recall.server

import im.ringlo.recall.Recall
import im.ringlo.recall.content.ContentResources
import im.ringlo.recall.content.ContentSnapshot
import java.util.concurrent.atomic.AtomicLong
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

class RecallServerReloadListener : SimplePreparableReloadListener<ContentSnapshot>() {
    private var current = ContentSnapshot.empty()

    override fun prepare(
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ): ContentSnapshot =
        ContentResources.load(resourceManager).copy(revision = NEXT_REVISION.incrementAndGet())

    override fun apply(
        snapshot: ContentSnapshot,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        current = snapshot
        Recall.LOGGER.info(
            "Loaded {} Recall guides (revision {})",
            snapshot.guides.size,
            snapshot.revision,
        )
    }

    fun snapshot(): ContentSnapshot = current

    companion object {
        private val NEXT_REVISION = AtomicLong()
    }
}
