package im.ringlo.recall.client.ui.components.nodecard.markdown

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.PngInfo

object MarkdownImages : ResourceManagerReloadListener {
    private val sizes = linkedMapOf<Identifier, Pair<Int, Int>?>()
    var revision = 0L
        private set

    fun resolve(destination: String, namespace: String): Identifier? {
        val value =
            destination.trim().removePrefix("resource:").substringBefore('#').removePrefix("/")
        if (value.isBlank()) return null
        val id =
            Identifier.tryParse(if (':' in value) value else "$namespace:$value") ?: return null
        if (id.path.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        val path = if (id.path.startsWith("textures/")) id.path else "textures/${id.path}"
        val filename = path.substringAfterLast('/')
        if ('.' in filename && !filename.endsWith(".png")) return null
        return id.withPath(if (filename.endsWith(".png")) path else "$path.png")
    }

    fun dimensions(texture: Identifier): Pair<Int, Int>? {
        if (texture in sizes) return sizes[texture]
        val size =
            try {
                Minecraft.getInstance()
                    .resourceManager
                    .getResource(texture)
                    .orElse(null)
                    ?.open()
                    ?.buffered()
                    ?.use { stream ->
                        stream.mark(32)
                        val info = PngInfo.fromStream(stream)
                        if (
                            info.width in 1..8192 &&
                                info.height in 1..8192 &&
                                info.width.toLong() * info.height <= 16_777_216
                        ) {
                            stream.reset()
                            NativeImage.read(stream).use { image -> image.width to image.height }
                        } else null
                    }
            } catch (_: Exception) {
                null
            }
        if (sizes.size >= 256) sizes.remove(sizes.keys.first())
        sizes[texture] = size
        return size
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        sizes.clear()
        revision++
    }
}
