// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;

import dev.aesi.api.RecipeAdapter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.*;

public final class VanillaRecipes implements RecipeAdapter {
    public static ItemStack sample(Ingredient ingredient) {
        var samples = ProcessingRecipes.items(ingredient, 1);
        return samples.isEmpty()
                ? ItemStack.EMPTY
                : ((AEItemKey) samples.getFirst().what()).toStack();
    }

    @SuppressWarnings("unchecked")
    public void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported) {
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            String id = holder.id().identifier().toString();
            if (!(recipe instanceof ShapedRecipe
                    || recipe instanceof ShapelessRecipe
                    || recipe instanceof AbstractCookingRecipe)) continue;
            try {
                if (recipe instanceof CraftingRecipe craft) {
                    int w, h;
                    List<ItemStack> grid;
                    if (recipe instanceof ShapedRecipe shaped) {
                        w = shaped.getWidth();
                        h = shaped.getHeight();
                        grid =
                                shaped.getIngredients().stream()
                                        .map(
                                                i ->
                                                        i.map(VanillaRecipes::sample)
                                                                .orElse(ItemStack.EMPTY))
                                        .toList();
                    } else {
                        grid =
                                new ArrayList<>(
                                        craft.placementInfo().ingredients().stream()
                                                .map(VanillaRecipes::sample)
                                                .toList());
                        if (grid.isEmpty()) continue;
                        w = Math.min(3, grid.size());
                        h = (grid.size() + w - 1) / w;
                        while (grid.size() < w * h) grid.add(ItemStack.EMPTY);
                    }
                    if (w > 3 || h > 3) {
                        unsupported.put(id, "Crafting grid exceeds 3x3");
                        continue;
                    }
                    var input = CraftingInput.of(w, h, grid);
                    if (!craft.matches(input, level)) {
                        unsupported.put(
                                id, "No safe representative for component/custom ingredient");
                        continue;
                    }
                    var result = craft.assemble(input);
                    if (result.isEmpty()) continue;
                    var encoded = new ItemStack[9];
                    Arrays.fill(encoded, ItemStack.EMPTY);
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) encoded[y * 3 + x] = grid.get(y * w + x);
                    var stack =
                            PatternDetailsHelper.encodeCraftingPattern(
                                    (RecipeHolder<CraftingRecipe>) holder,
                                    encoded,
                                    result,
                                    true,
                                    false);
                    output.add(
                            new Blueprint(
                                    id,
                                    "crafting",
                                    VirtualPatterns.create(level, id, "crafting", recipe, stack),
                                    20,
                                    false,
                                    0));
                } else if (recipe instanceof AbstractCookingRecipe cooking
                        && (recipe.getType() == RecipeType.SMELTING
                                || recipe.getType() == RecipeType.BLASTING
                                || recipe.getType() == RecipeType.SMOKING)) {
                    var ingredient = cooking.input();
                    for (var candidate : ProcessingRecipes.items(ingredient, 1)) {
                        var input = ((AEItemKey) candidate.what()).toStack();
                        if (!ingredient.test(input)) continue;
                        var result = cooking.assemble(new SingleRecipeInput(input));
                        String family =
                                recipe.getType() == RecipeType.BLASTING
                                        ? "blasting"
                                        : recipe.getType() == RecipeType.SMOKING
                                                ? "smoking"
                                                : "smelting";
                        var stack =
                                PatternDetailsHelper.encodeProcessingPattern(
                                        List.of(new GenericStack(AEItemKey.of(input), 1)),
                                        List.of(
                                                new GenericStack(
                                                        AEItemKey.of(result), result.getCount())));
                        output.add(
                                new Blueprint(
                                        id,
                                        family,
                                        VirtualPatterns.create(level, id, family, recipe, stack),
                                        cooking.cookingTime(),
                                        true,
                                        0));
                    }
                }
            } catch (RuntimeException error) {
                unsupported.put(id, error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }
    }
}
