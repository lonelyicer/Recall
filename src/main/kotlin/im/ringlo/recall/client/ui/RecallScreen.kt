package im.ringlo.recall.client.ui

import im.ringlo.recall.client.ui.components.RecallRoot
import im.ringlo.recall.client.ui.components.scene.ScenePreviewComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class RecallScreen(private val parent: Screen?, private val item: Identifier) :
    Screen(Component.translatable("screen.recall.title")) {
    private lateinit var root: RecallRoot
    private var lastFrameNanos = 0L

    override fun init() {
        val first = !::root.isInitialized
        if (first) root = RecallRoot(font)
        lastFrameNanos = 0L
        root.layoutIfNeeded(width, height)
        if (first) root.openItem(item)
    }

    fun isItemSubject(item: Identifier): Boolean = ::root.isInitialized && root.isItemSubject(item)

    fun toggleSceneFullscreen(scene: ScenePreviewComponent) = root.toggleSceneFullscreen(scene)

    override fun tick() {
        root.tick()
    }

    override fun extractRenderState(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        root.layoutIfNeeded(width, height)
        val now = System.nanoTime()
        val delta =
            if (lastFrameNanos == 0L) 0f
            else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = now
        root.frame(delta)
        root.extract(gui, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        root.mouseClicked(event, doubleClick)

    override fun mouseReleased(event: MouseButtonEvent): Boolean = root.mouseReleased(event)

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        root.mouseDragged(event, dragX, dragY)

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean = root.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

    override fun keyPressed(event: KeyEvent): Boolean =
        root.keyPressed(event) || super.keyPressed(event)

    override fun keyReleased(event: KeyEvent): Boolean =
        root.keyReleased(event) || super.keyReleased(event)

    override fun charTyped(event: CharacterEvent): Boolean =
        root.charTyped(event) || super.charTyped(event)

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        if (::root.isInitialized) root.dispose()
        super.removed()
    }

    override fun shouldCloseOnEsc(): Boolean = true

    override fun isPauseScreen(): Boolean = false

    override fun isInGameUi(): Boolean = false
}
