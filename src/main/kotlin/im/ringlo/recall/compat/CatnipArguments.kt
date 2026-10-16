package im.ringlo.recall.compat

import im.ringlo.recall.Recall
import net.createmod.catnip.impl.command.argument.ConfigIdArgument
import net.createmod.catnip.impl.command.argument.ConfigPathArgument
import net.createmod.catnip.impl.command.argument.ModIdArgument
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.core.registries.Registries
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

@Deprecated("this will be removed when upstream fixed")
object CatnipArguments {
    private val types =
        DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, Recall.ID).apply {
            register("catnip_mod_id") { _ ->
                ArgumentTypeInfos.registerByClass(
                    ModIdArgument::class.java,
                    SingletonArgumentInfo.contextFree(ModIdArgument::modId),
                )
            }
            register("catnip_config_id") { _ ->
                ArgumentTypeInfos.registerByClass(
                    ConfigIdArgument::class.java,
                    SingletonArgumentInfo.contextFree(ConfigIdArgument::configId),
                )
            }
            register("catnip_config_path") { _ ->
                ArgumentTypeInfos.registerByClass(
                    ConfigPathArgument::class.java,
                    SingletonArgumentInfo.contextFree(ConfigPathArgument::path),
                )
            }
        }

    fun register(bus: IEventBus) = types.register(bus)
}
