// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.crafting.*;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class VirtualPatterns {
    private static final java.util.Map<
                    Recipe<?>, java.util.Map<appeng.api.stacks.AEItemKey, Boolean>>
            CURRENT_MATCHES = new java.util.WeakHashMap<>();

    private VirtualPatterns() {}

    public static IPatternDetails create(
            ServerLevel level,
            String id,
            String family,
            Recipe<?> recipe,
            ItemStack nativePattern) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var source = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        if (family.equals("crafting")) source = freezeIngredients((CompoundTag) source, ops);
        return createDeclared(level, id, family, (CompoundTag) source, nativePattern);
    }

    /**
     * For explicit, deterministic hardcoded machine processes that have no datapack Recipe object.
     */
    public static IPatternDetails createDeclared(
            ServerLevel level,
            String id,
            String family,
            CompoundTag source,
            ItemStack nativePattern) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var contents = new ListTag();
        contents.add(source);
        contents.add(ItemStack.CODEC.encodeStart(ops, nativePattern).getOrThrow());
        var tag = new CompoundTag();
        tag.putString("aesi_schema", "2");
        tag.putString("aesi_recipe", id);
        tag.putString("aesi_family", family);
        tag.putString("aesi_fingerprint", fingerprint(canonicalNbt(contents)));
        if (family.equals("crafting"))
            tag.putString("aesi_recipe_data", pack((CompoundTag) source));
        nativePattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return java.util.Objects.requireNonNull(
                PatternDetailsHelper.decodePattern(nativePattern, level));
    }

    private static CompoundTag freezeIngredients(
            CompoundTag source, com.mojang.serialization.DynamicOps<Tag> ops) {
        var frozen = source.copy();
        if (frozen.get("key") instanceof CompoundTag keys)
            for (var key : keys.keySet()) keys.put(key, freezeIngredient(keys.get(key), ops));
        if (frozen.get("ingredients") instanceof ListTag ingredients) {
            var list = new ListTag();
            for (var ingredient : ingredients) list.add(freezeIngredient(ingredient, ops));
            frozen.put("ingredients", list);
        }
        return frozen;
    }

    private static Tag freezeIngredient(Tag encoded, com.mojang.serialization.DynamicOps<Tag> ops) {
        var ingredient =
                net.minecraft.world.item.crafting.Ingredient.CODEC.parse(ops, encoded).getOrThrow();
        if (ingredient.isSimple())
            ingredient =
                    net.minecraft.world.item.crafting.Ingredient.of(
                            ingredient.items().map(h -> h.value()));
        else if (ingredient.getCustomIngredient()
                instanceof
                net.neoforged.neoforge.common.crafting.DataComponentIngredient components)
            ingredient =
                    new net.neoforged.neoforge.common.crafting.DataComponentIngredient(
                                    net.minecraft.core.HolderSet.direct(
                                            components.itemSet().stream().toList()),
                                    components.components(),
                                    components.componentsExhaustive())
                            .toVanilla();
        else {
            var choices = ProcessingRecipes.items(ingredient, 1);
            ingredient =
                    net.neoforged.neoforge.common.crafting.CompoundIngredient.of(
                            choices.stream()
                                    .map(
                                            s ->
                                                    net.neoforged.neoforge.common.crafting
                                                            .DataComponentIngredient.of(
                                                            true,
                                                            ((appeng.api.stacks.AEItemKey) s.what())
                                                                    .toStack()))
                                    .toArray(net.minecraft.world.item.crafting.Ingredient[]::new));
        }
        return net.minecraft.world.item.crafting.Ingredient.CODEC
                .encodeStart(ops, ingredient)
                .getOrThrow();
    }

    public static boolean isVirtual(IPatternDetails pattern) {
        var schema =
                pattern.getDefinition()
                        .toStack()
                        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag()
                        .getString("aesi_schema")
                        .orElse("");
        return schema.equals("1") || schema.equals("2");
    }

    public static void invalidate() {
        CURRENT_MATCHES.clear();
    }

    public static boolean matchesCurrentRecipe(
            ServerLevel level, Recipe<?> recipe, Blueprint blueprint) {
        var definition = blueprint.pattern().getDefinition();
        return CURRENT_MATCHES
                .computeIfAbsent(recipe, r -> new java.util.HashMap<>())
                .computeIfAbsent(
                        definition,
                        key -> {
                            var stack = key.toStack();
                            var meta =
                                    stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                                            .copyTag();
                            stack.remove(DataComponents.CUSTOM_DATA);
                            var ops =
                                    level.registryAccess()
                                            .createSerializationContext(NbtOps.INSTANCE);
                            var source = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow();
                            if (blueprint.family().equals("crafting"))
                                source = freezeIngredients((CompoundTag) source, ops);
                            var contents = new ListTag();
                            contents.add(source);
                            contents.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
                            return fingerprint(canonicalNbt(contents))
                                    .equals(meta.getString("aesi_fingerprint").orElse(""));
                        });
    }

    public static String identity(ServerLevel level, IPatternDetails pattern) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return fingerprint(
                canonicalNbt(
                        ItemStack.CODEC
                                .encodeStart(ops, pattern.getDefinition().toStack())
                                .getOrThrow()));
    }

    public static net.minecraft.world.item.crafting.RecipeHolder<?> frozenCraftingRecipe(
            appeng.api.stacks.AEItemKey definition,
            net.minecraft.world.level.Level level,
            net.minecraft.resources.ResourceKey<Recipe<?>> id) {
        var stack = definition.toStack();
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getString("aesi_schema").orElse("").equals("2")
                || !tag.getString("aesi_family").orElse("").equals("crafting")) return null;
        var encoded = unpack(tag.getString("aesi_recipe_data").orElseThrow());
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        stack.remove(DataComponents.CUSTOM_DATA);
        var contents = new ListTag();
        contents.add(encoded);
        contents.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        if (!fingerprint(canonicalNbt(contents))
                .equals(tag.getString("aesi_fingerprint").orElse("")))
            throw new IllegalArgumentException("AESI frozen recipe fingerprint mismatch");
        var recipe = Recipe.CODEC.parse(ops, encoded).getOrThrow();
        if (!(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe
                || recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe))
            throw new IllegalArgumentException(
                    "AESI frozen recipe is not deterministic 3x3 crafting");
        return new net.minecraft.world.item.crafting.RecipeHolder<>(id, recipe);
    }

    private static String pack(CompoundTag data) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed(data, bytes);
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Recipe snapshot", error);
        }
    }

    private static CompoundTag unpack(String data) {
        if (data.length() > 1048576)
            throw new IllegalArgumentException("Recipe snapshot exceeds limit");
        try {
            return NbtIo.readCompressed(
                    new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data)),
                    NbtAccounter.create(1048576));
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Recipe snapshot", error);
        }
    }

    private static String canonicalNbt(Tag data) {
        if (data instanceof CompoundTag compound) {
            var out = new StringBuilder("compound{");
            compound.keySet().stream()
                    .sorted()
                    .forEach(
                            k ->
                                    out.append(k.length())
                                            .append(':')
                                            .append(k)
                                            .append('=')
                                            .append(canonicalNbt(compound.get(k)))
                                            .append(';'));
            return out.append('}').toString();
        }
        if (data instanceof ListTag list) {
            var out = new StringBuilder("list[");
            list.forEach(v -> out.append(canonicalNbt(v)).append(';'));
            return out.append(']').toString();
        }
        return data.getId() + ":" + data;
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
