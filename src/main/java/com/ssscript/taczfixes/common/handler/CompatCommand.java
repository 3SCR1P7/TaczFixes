package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.CompatStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** /taczfixes compat: 列出与 taczfixes 有联动的模组的安装状态与版本(0 级权限)。 */
public final class CompatCommand {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taczfixes")
                .then(Commands.literal("compat")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            for (Component line : CompatStatus.feedbackLines()) {
                                source.sendSuccess(() -> line, false);
                            }
                            return 1;
                        })));
    }
}
