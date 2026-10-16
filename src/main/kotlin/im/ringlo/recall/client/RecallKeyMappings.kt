package im.ringlo.recall.client

import com.mojang.blaze3d.platform.InputConstants
import im.ringlo.recall.Recall
import net.createmod.catnip.api.client.platform.ModClientHooksHelper
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

object RecallKeyMappings {
    private val category = KeyMapping.Category(Recall.id("ui"))
    val open =
        KeyMapping("key.recall.open", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category)

    fun register(event: RegisterKeyMappingsEvent) {
        event.registerCategory(category)
        event.register(open)
    }

    fun isOpenKeyDown(): Boolean =
        !open.isUnbound && ModClientHooksHelper.INSTANCE.isKeyPressed(open)
}
