// SPDX-License-Identifier: MIT
package dev.aesi.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.RecipeAccess;
import appeng.crafting.pattern.AECraftingPattern;

import dev.aesi.recipe.VirtualPatterns;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/**
 * Keep native molecular-assembler semantics while decoding the original task's immutable crafting
 * recipe.
 */
@Mixin(value = AECraftingPattern.class, remap = false)
public abstract class FrozenCraftingPatternMixin {
    @Shadow @Final private AEItemKey definition;

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/crafting/RecipeAccess;byKey(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/item/crafting/RecipeHolder;"))
    private RecipeHolder<?> aesiOriginalRecipe(
            Level level, RecipeType<CraftingRecipe> type, ResourceKey<Recipe<?>> id) {
        var frozen = VirtualPatterns.frozenCraftingRecipe(definition, level, id);
        return frozen != null ? frozen : RecipeAccess.byKey(level, type, id);
    }
}
