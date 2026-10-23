// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import dev.aesi.multiblock.Workstation;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AesiCommands {
    private AesiCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aesi")
            .then(Commands.literal("layout").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("message.aesi.layout"), false);
                return 1;
            }))
            .then(Commands.literal("status")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(ctx -> {
                    var pos = BlockPosArgument.getLoadedBlockPos(ctx, "position");
                    var level = ctx.getSource().getLevel();
                    var controller = level.getBlockEntity(pos) instanceof SmartController c ? c
                            : Workstation.owner(level, pos, level.getBlockState(pos));
                    if (controller == null) {
                        ctx.getSource().sendFailure(Component.literal("No AESI controller at " + pos.toShortString()));
                        return 0;
                    }
                    var logic = (SmartLogic) controller.getLogic();
                    ctx.getSource().sendSuccess(() -> Component.literal("AESI 2 x 4 x 2: formed="
                            + controller.structureReady() + "; active=" + controller.getMainNode().isActive()
                            + "; disk arrays=" + logic.diskCount() + "/" + SmartLogic.ARRAY_SLOTS + "; pattern capacity=" + logic.patternCapacity()
                            + "; virtual patterns=" + logic.getAvailablePatterns().size()), false);
                    for (var problem : Workstation.problems(controller, true))
                        ctx.getSource().sendSuccess(() -> problem, false);
                    for (var line : logic.diagnostics())
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    for (var line : NetworkRuntime.lastDecision(logic.getGrid()))
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                    return 1;
                }))));
    }
}
