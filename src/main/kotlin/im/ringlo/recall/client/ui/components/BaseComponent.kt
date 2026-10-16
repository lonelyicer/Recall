package im.ringlo.recall.client.ui.components

import im.ringlo.recall.client.ui.UiRect
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

abstract class BaseComponent(val key: String) {
    val bounds = LayoutBox()
    private val ownedChildren = mutableListOf<BaseComponent>()
    val children: List<BaseComponent>
        get() = ownedChildren

    private var parent: BaseComponent? = null
    private var captured: BaseComponent? = null
    private var ownCapture = false
    private var dirty = true
    var visible = true
    var enabled = true
    var focused = false
    var clip: UiRect? = null

    fun <T : BaseComponent> add(child: T): T {
        require(child !== this && child.parent == null) {
            "Component ${child.key} already has an owner"
        }
        var ancestor: BaseComponent? = this
        while (ancestor != null) {
            require(ancestor !== child)
            ancestor = ancestor.parent
        }
        child.parent = this
        ownedChildren += child
        invalidate()
        return child
    }

    fun remove(child: BaseComponent) {
        if (ownedChildren.remove(child)) {
            if (captured === child) captured = null
            child.parent = null
            child.dispose()
            invalidate()
        }
    }

    open fun dispose() {
        captured = null
        ownCapture = false
        children.forEach { it.dispose() }
    }

    fun invalidate() {
        dirty = true
        parent?.invalidate()
    }

    fun isDirty(): Boolean = dirty

    open fun layout(box: LayoutBox) {
        bounds.x = box.x
        bounds.y = box.y
        bounds.width = box.width.coerceAtLeast(0)
        bounds.height = box.height.coerceAtLeast(0)
        dirty = false
    }

    open fun extract(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible) return
        val clipping = clip
        if (clipping != null) {
            if (clipping.width <= 0f || clipping.height <= 0f) return
            gui.enableScissor(
                kotlin.math.floor(clipping.left).toInt(),
                kotlin.math.floor(clipping.top).toInt(),
                kotlin.math.ceil(clipping.right).toInt(),
                kotlin.math.ceil(clipping.bottom).toInt(),
            )
        }
        try {
            paint(gui, mouseX, mouseY, partialTick)
            children.forEach { it.extract(gui, mouseX, mouseY, partialTick) }
        } finally {
            if (clipping != null) gui.disableScissor()
        }
    }

    protected open fun paint(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) = Unit

    open fun frame(seconds: Float) {
        children.forEach { it.frame(seconds) }
    }

    open fun tick() {
        children.forEach { it.tick() }
    }

    protected open fun hitTest(x: Double, y: Double): Boolean = bounds.contains(x, y)

    open fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (
            !visible ||
                !enabled ||
                !hitTest(event.x(), event.y()) ||
                clip?.contains(event.x().toFloat(), event.y().toFloat()) == false
        )
            return false
        for (child in children.asReversed()) {
            if (child.mouseClicked(event, doubleClick)) {
                captured = child
                return true
            }
        }
        ownCapture = onMouseClicked(event, doubleClick)
        return ownCapture
    }

    open fun mouseReleased(event: MouseButtonEvent): Boolean {
        val child = captured
        captured = null
        val own = ownCapture
        ownCapture = false
        return child?.mouseReleased(event) ?: (own && onMouseReleased(event))
    }

    open fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean =
        captured?.mouseDragged(event, dragX, dragY)
            ?: (ownCapture && onMouseDragged(event, dragX, dragY))

    open fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (
            !visible ||
                !enabled ||
                !hitTest(mouseX, mouseY) ||
                clip?.contains(mouseX.toFloat(), mouseY.toFloat()) == false
        )
            return false
        return children.asReversed().any { it.mouseScrolled(mouseX, mouseY, scrollX, scrollY) } ||
            onMouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    open fun keyPressed(event: KeyEvent): Boolean =
        visible &&
            enabled &&
            (children.asReversed().any { it.keyPressed(event) } || onKeyPressed(event))

    open fun keyReleased(event: KeyEvent): Boolean =
        visible && enabled && children.asReversed().any { it.keyReleased(event) }

    open fun charTyped(event: CharacterEvent): Boolean =
        visible &&
            enabled &&
            (children.asReversed().any { it.charTyped(event) } || onCharTyped(event))

    protected open fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        false

    protected open fun onMouseReleased(event: MouseButtonEvent): Boolean = true

    protected open fun onMouseDragged(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean = false

    protected open fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean = false

    protected open fun onKeyPressed(event: KeyEvent): Boolean = false

    protected open fun onCharTyped(event: CharacterEvent): Boolean = false
}
