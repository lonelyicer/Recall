package im.ringlo.recall.client.ui.components.scene

import im.ringlo.recall.Recall
import im.ringlo.recall.client.scene.SceneInstance
import im.ringlo.recall.client.scene.SceneRenderState
import im.ringlo.recall.client.scene.SceneTransform
import im.ringlo.recall.client.ui.RecallDraw
import im.ringlo.recall.client.ui.RecallScreen
import im.ringlo.recall.client.ui.components.BaseComponent
import im.ringlo.recall.client.ui.components.layout.LayoutBox
import im.ringlo.recall.content.ContentSnapshot
import kotlin.math.exp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Vector3f

class ScenePreviewComponent(
    private val font: Font,
    private val sceneProvider: () -> ContentSnapshot.SceneDefinition?,
) : BaseComponent("scene-preview"), ScenePlayback {
    private var installed: ContentSnapshot.SceneDefinition? = null
    private var instance: SceneInstance? = null
    private var failed = false
    private var time = 0f
    private var playing = true
    private var rotating = false
    private val transform = SceneTransform()
    private val toolbar =
        add(
            SceneToolbarComponent(
                font,
                { instance?.title ?: sceneProvider()?.title.orEmpty() },
                { playing },
                { expanded },
                ::resetPlayback,
                ::togglePlayback,
                ::toggleFullscreen,
                { opacity },
            )
        )
    private val progressBar = add(SceneProgressBar(this) { opacity })
    var expanded = false
    var disposed = false
        private set

    var presentation: ScenePresentation? = null
        private set

    var opacity = 1f
    override val currentTime: Float
        get() = time

    override val totalTime: Float
        get() = sceneProvider()?.duration ?: 0f

    override val keyframes: List<Float>
        get() =
            (sceneProvider()
                    ?.steps
                    .orEmpty()
                    .filter {
                        it.type.namespace == Recall.ID &&
                            (it.action in setOf("keyframe", "lazy_keyframe") ||
                                it.parameters.get("keyframe")?.asBoolean == true)
                    }
                    .map { it.time + if (it.action == "lazy_keyframe") it.duration else 0f } +
                    instance?.keyframes.orEmpty())
                .distinct()
                .sorted()

    override fun layout(box: LayoutBox) {
        super.layout(box)
        toolbar.layout(LayoutBox(bounds.x, bounds.y, bounds.width, 25))
        progressBar.layout(
            LayoutBox(
                bounds.x + 6,
                bounds.bottom - 27,
                (bounds.width - 12).coerceAtLeast(1),
                25,
            )
        )
    }

    override fun extract(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (expanded || !visible || opacity <= 0.001f || bounds.width <= 16 || bounds.height <= 48)
            return
        super.extract(gui, mouseX, mouseY, partialTick)
    }

    override fun frame(seconds: Float) {
        if (visible && enabled && playing) {
            time = (time + seconds).coerceAtMost(totalTime)
            if (time >= totalTime) playing = false
        }
        super.frame(seconds)
    }

    private fun resetPlayback() {
        seekToTime(0f)
        transform.reset()
        playing = true
    }

    private fun toggleFullscreen() {
        (Minecraft.getInstance().screen as? RecallScreen)?.toggleSceneFullscreen(this)
    }

    fun togglePlayback() {
        if (!playing && time >= totalTime) time = 0f
        playing = !playing
    }

    override fun seekToTime(seconds: Float) {
        time = seconds.coerceIn(0f, totalTime)
        instance?.seek(time)
    }

    private fun install(scene: ContentSnapshot.SceneDefinition): SceneInstance? {
        val level = Minecraft.getInstance().level ?: return null
        if (installed != scene || (instance != null && instance?.sourceLevel !== level)) {
            instance?.close()
            instance = null
            installed = scene
            time = 0f
            playing = true
            progressBar.reset()
            rotating = false
            failed = false
            transform.reset()
        }
        if (instance == null && !failed) {
            try {
                instance = SceneInstance(scene, level)
            } catch (exception: Exception) {
                failed = true
                Recall.LOGGER.error("Could not load Recall scene {}", scene.id, exception)
            }
        }
        return instance
    }

    override fun paint(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (expanded) return
        presentation =
            ScenePresentation.capture(
                gui,
                ScreenRectangle(bounds.x, bounds.y, bounds.width, bounds.height),
            )
        paintScene(gui, mouseX, mouseY, partialTick)
    }

    fun paintExpanded(gui: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!visible || opacity <= 0.001f || bounds.width <= 16 || bounds.height <= 48) return
        paintScene(gui, mouseX, mouseY, partialTick)
        toolbar.extract(gui, mouseX, mouseY, partialTick)
        progressBar.extract(gui, mouseX, mouseY, partialTick)
    }

    private fun paintScene(
        gui: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val scene = sceneProvider()
        if (scene == null) {
            disposeScene()
            return
        }
        val left = bounds.x
        val top = bounds.y
        val bottom = bounds.bottom
        if (bounds.width <= 16 || bounds.height <= 48 || opacity <= 0f) return
        fun color(value: Int) = RecallDraw.withAlpha(value, opacity)

        val viewport = ScreenRectangle(left, top, bounds.width, bounds.height)
        val visibleViewport =
            viewport.transformMaxBounds(gui.pose()).let { rect ->
                gui.peekScissorStack()?.intersection(rect)
                    ?: if (gui.peekScissorStack() == null) rect else null
            }
        var liveScene: SceneInstance? = null
        if (
            visibleViewport != null && visibleViewport.width() > 0 && visibleViewport.height() > 0
        ) {
            liveScene = install(scene)
            if (liveScene != null && scene.blocks.isNotEmpty()) {
                try {
                    liveScene.seek(time)
                    transform.sceneYaw = liveScene.cameraYaw
                    transform.scenePitch = liveScene.cameraPitch
                    transform.sceneScale = liveScene.cameraScale
                    transform.sceneOffset = liveScene.cameraOffset
                    val scale = Minecraft.getInstance().window.guiScale
                    val matrix =
                        transform.matrix(liveScene.bounds, bounds.width, bounds.height, scale)
                    val pointer =
                        Vector3f(
                            (mouseX - left) * scale.toFloat(),
                            (bottom - mouseY) * scale.toFloat(),
                            -600f,
                        )
                    Matrix4f(matrix).invert().transformPosition(pointer)
                    liveScene.cursor =
                        Vec3(pointer.x.toDouble(), pointer.y.toDouble(), pointer.z.toDouble())
                    gui.submitPictureInPictureRenderState(
                        SceneRenderState(
                            liveScene,
                            time,
                            matrix,
                            transform.camera(liveScene.bounds),
                            opacity,
                            Matrix3x2f(gui.pose()),
                            viewport,
                            visibleViewport,
                        )
                    )
                } catch (exception: Exception) {
                    failed = true
                    playing = false
                    instance?.close()
                    instance = null
                    liveScene = null
                    Recall.LOGGER.error(
                        "Could not execute Recall scene {} at {}",
                        scene.id,
                        time,
                        exception,
                    )
                }
            }
        }

        gui.nextStratum()
        if (!expanded)
            gui.outline(left, top, bounds.width, bounds.height, color(0x907B7B9A.toInt()))
        if (liveScene != null) {
            gui.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom())
            try {
                val scale = Minecraft.getInstance().window.guiScale
                val matrix = transform.matrix(liveScene.bounds, bounds.width, bounds.height, scale)
                liveScene.overlays.values.forEach { it.extract(gui, viewport, matrix, scale) }
            } finally {
                gui.disableScissor()
            }
        } else {
            val message =
                if (failed) "Scene unavailable"
                else if (scene.blocks.isEmpty()) "No scene blocks"
                else "Join a world to view this scene"
            gui.text(
                font,
                font.plainSubstrByWidth(message, (bounds.width - 16).coerceAtLeast(1)),
                left + 8,
                top + 32,
                color(RecallDraw.MUTED),
            )
        }
    }

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return false
        rotating = false
        val viewport = bounds.contains(event.x(), event.y())
        val controls = event.y() < bounds.y + 25 || event.y() >= bounds.bottom - 30
        if (viewport && !controls) {
            if (doubleClick) transform.reset() else rotating = true
        }
        return viewport
    }

    override fun onMouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (rotating) {
            transform.yaw = (transform.yaw + dragX.toFloat()) % 360f
            transform.pitch = (transform.pitch + dragY.toFloat()).coerceIn(-85f, 15f)
        }
        return true
    }

    override fun onMouseReleased(event: MouseButtonEvent): Boolean {
        rotating = false
        return true
    }

    override fun onMouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double,
    ): Boolean {
        if (mouseY < bounds.y + 25 || mouseY >= bounds.bottom - 30) return false
        transform.zoom = (transform.zoom * exp(scrollY.toFloat() * 0.1f)).coerceIn(0.5f, 2.5f)
        return true
    }

    private fun disposeScene() {
        instance?.close()
        instance = null
        installed = null
        failed = false
    }

    override fun dispose() {
        disposed = true
        disposeScene()
        super.dispose()
    }
}
