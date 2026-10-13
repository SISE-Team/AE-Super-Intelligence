// SPDX-License-Identifier: MIT
package dev.aesi.controller;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AesiCommands {
    private AesiCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("aesi")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(
                                        Commands.literal("status")
                                                .then(
                                                        Commands.argument(
                                                                        "x",
                                                                        IntegerArgumentType
                                                                                .integer())
                                                                .then(
                                                                        Commands.argument(
                                                                                        "y",
                                                                                        IntegerArgumentType
                                                                                                .integer())
                                                                                .then(
                                                                                        Commands
                                                                                                .argument(
                                                                                                        "z",
                                                                                                        IntegerArgumentType
                                                                                                                .integer())
                                                                                                .executes(
                                                                                                        ctx -> {
                                                                                                            var
                                                                                                                    pos =
                                                                                                                            new BlockPos(
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    ctx,
                                                                                                                                                    "x"),
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    ctx,
                                                                                                                                                    "y"),
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    ctx,
                                                                                                                                                    "z"));
                                                                                                            if (ctx.getSource()
                                                                                                                            .getLevel()
                                                                                                                            .getBlockEntity(
                                                                                                                                    pos)
                                                                                                                    instanceof
                                                                                                                    SmartController
                                                                                                                            controller) {
                                                                                                                var
                                                                                                                        logic =
                                                                                                                                (SmartLogic)
                                                                                                                                        controller
                                                                                                                                                .getLogic();
                                                                                                                ctx.getSource()
                                                                                                                        .sendSuccess(
                                                                                                                                () ->
                                                                                                                                        Component
                                                                                                                                                .literal(
                                                                                                                                                        "AESI:"
                                                                                                                                                            + " "
                                                                                                                                                                + logic.getAvailablePatterns()
                                                                                                                                                                        .size()
                                                                                                                                                                + " virtual"
                                                                                                                                                                + " patterns;"
                                                                                                                                                                + " active="
                                                                                                                                                                + controller
                                                                                                                                                                        .getMainNode()
                                                                                                                                                                        .isActive()),
                                                                                                                                false);
                                                                                                                for (var
                                                                                                                        line :
                                                                                                                                logic
                                                                                                                                        .diagnostics())
                                                                                                                    ctx.getSource()
                                                                                                                            .sendSuccess(
                                                                                                                                    () ->
                                                                                                                                            Component
                                                                                                                                                    .literal(
                                                                                                                                                            line),
                                                                                                                                    false);
                                                                                                                for (var
                                                                                                                        line :
                                                                                                                                NetworkRuntime
                                                                                                                                        .lastDecision(
                                                                                                                                                logic
                                                                                                                                                        .getGrid()))
                                                                                                                    ctx.getSource()
                                                                                                                            .sendSuccess(
                                                                                                                                    () ->
                                                                                                                                            Component
                                                                                                                                                    .literal(
                                                                                                                                                            line),
                                                                                                                                    false);
                                                                                                                return 1;
                                                                                                            }
                                                                                                            ctx.getSource()
                                                                                                                    .sendFailure(
                                                                                                                            Component
                                                                                                                                    .literal(
                                                                                                                                            "No AESI"
                                                                                                                                                + " controller"
                                                                                                                                                + " at "
                                                                                                                                                    + pos));
                                                                                                            return 0;
                                                                                                        }))))));
    }
}
