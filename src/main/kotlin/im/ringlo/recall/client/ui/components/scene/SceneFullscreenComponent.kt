package im.ringlo.recall.client.ui.components.scene

import com.mojang.blaze3d.platform.InputConstants
import im.ringlo.recall.client.ui.RecallSpring
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class SceneFullscreenComponent : BaseComponent("scene-fullscreen") {
    private var scene: ScenePreviewComponent? = null
    private var origin: ScenePresentation? = null
    private var presentation: ScenePresentation? = null
    private val expansion = RecallSpring(.36f)
    private var closing = false

    init {
        visible = false
    }

    fun open(scene: ScenePreviewComponent) {
        this.scene?.expanded = false
        this.scene = scene
        origin = scene.presentation
        scene.expanded = true
        closing = false
        visible = true
        expansion.snap(0f)
        expansion.setTarget(1f)
    }

    fun close(): Boolean {
        if (scene == null) return false
        closing = true
        expansion.setTarget(0f)
        return true
    }

    override fun frame(seconds: Float) {
        expansion.tick(seconds)
        if (scene?.disposed == true || closing && expansion.settled()) release()
    }

    private fun release() {
        scene?.expanded = false
        scene = null
        visible = false
        presentation = null
        origin = null
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val scene = scene ?: return
        val target = ScenePresentation.fullscreen(bounds.width, bounds.height)
        val progress = expansion.value.coerceIn(0f, 1f)
        val p = (origin ?: target).interpolate(target, progress)
        presentation = p
        gui.nextStratum()
        gui.fill(0, 0, bounds.width, bounds.height, ((232 * progress).toInt() shl 24) or 0x101018)
        gui.enableScissor(p.clip.left(), p.clip.top(), p.clip.right(), p.clip.bottom())
        gui.pose().pushMatrix()
        try {
            gui.pose().translate(p.x, p.y)
            gui.pose().scale(p.scale)
            withScene {
                it.paintExpanded(
                    gui,
                    p.localX(mouseX.toDouble()).toInt(),
                    p.localY(mouseY.toDouble()).toInt(),
                    partialTick,
                )
            }
        } finally {
            gui.pose().popMatrix()
            gui.disableScissor()
        }
    }

    private fun withScene(action: (ScenePreviewComponent) -> Unit) {
        val scene = scene ?: return
        val p = presentation ?: return
        val original =
            LayoutBox(scene.bounds.x, scene.bounds.y, scene.bounds.width, scene.bounds.height)
        val clip = scene.clip
        scene.layout(
            LayoutBox(0, 0, p.width.toInt().coerceAtLeast(1), p.height.toInt().coerceAtLeast(1))
        )
        scene.clip = null
        try {
            action(scene)
        } finally {
            scene.layout(original)
            scene.clip = clip
        }
    }

    private fun mouse(event: MouseButtonEvent): MouseButtonEvent {
        val p = presentation ?: return event
        return MouseButtonEvent(p.localX(event.x()), p.localY(event.y()), event.buttonInfo())
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!closing) withScene { it.mouseClicked(mouse(event), doubleClick) }
        return true
    }

    override fun onMouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!closing)
            withScene {
                it.mouseDragged(
                    mouse(event),
                    dragX / (presentation?.scale ?: 1f),
                    dragY / (presentation?.scale ?: 1f),
                )
            }
        return true
    }

    override fun onMouseReleased(event: MouseButtonEvent): Boolean {
        withScene { it.mouseReleased(mouse(event)) }
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        val p = presentation
        if (!closing && p != null)
            withScene { it.mouseScrolled(p.localX(mouseX), p.localY(mouseY), scrollX, scrollY) }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!visible) return false
        if (event.key() == InputConstants.KEY_ESCAPE) close()
        if (!closing && event.key() == InputConstants.KEY_SPACE) scene?.togglePlayback()
        return true
    }

    override fun dispose() {
        release()
        super.dispose()
    }
}
