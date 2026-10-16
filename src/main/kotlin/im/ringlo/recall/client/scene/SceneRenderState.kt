package im.ringlo.recall.client.scene

import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix4f

class SceneRenderState(
    val scene: SceneInstance,
    val time: Float,
    val transform: Matrix4f,
    val camera: CameraRenderState,
    val opacity: Float,
    private val guiPose: Matrix3x2f,
    private val rectangle: ScreenRectangle,
    private val scissor: ScreenRectangle?,
) : PictureInPictureRenderState {
    override fun x0(): Int = rectangle.left()

    override fun y0(): Int = rectangle.top()

    override fun x1(): Int = rectangle.right()

    override fun y1(): Int = rectangle.bottom()

    override fun scale(): Float = 1f

    override fun pose(): Matrix3x2f = guiPose

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? {
        val transformed = rectangle.transformMaxBounds(guiPose)
        return if (scissor == null) transformed else transformed.intersection(scissor)
    }
}
