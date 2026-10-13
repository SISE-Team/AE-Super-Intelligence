// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.stacks.AEKey;

import dev.aesi.api.*;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.*;

public final class RecipeCatalog {
    private static final Map<MinecraftServer, RecipeCatalog> CACHED = new WeakHashMap<>();
    private static final Map<MinecraftServer, LinkedHashMap<Set<AEKey>, RecipeCatalog>>
            COMPONENT_CACHED = new WeakHashMap<>();
    private final Map<AEKey, List<Blueprint>> byOutput = new HashMap<>();
    private final Map<String, String> unsupported = new TreeMap<>();

    private RecipeCatalog(ServerLevel level) {
        var recipes = new ArrayList<Blueprint>();
        new VanillaRecipes().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("modern_industrialization"))
            new dev.aesi.compat.ModernIndustrializationAdapter().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("appmek"))
            new dev.aesi.compat.MekanismAdapter().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("ic2"))
            new dev.aesi.compat.IndustrialCraftAdapter().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("industrialforegoing"))
            new dev.aesi.compat.IndustrialForegoingAdapter().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("enderio"))
            new dev.aesi.compat.EnderIOAdapter().read(level, recipes, unsupported);
        if (ModList.get().isLoaded("oritech"))
            new dev.aesi.compat.OritechAdapter().read(level, recipes, unsupported);
        ServiceLoader.load(RecipeAdapter.class)
                .forEach(adapter -> adapter.read(level, recipes, unsupported));
        for (var recipe : recipes)
            byOutput.computeIfAbsent(
                            recipe.pattern().getPrimaryOutput().what(), k -> new ArrayList<>())
                    .add(recipe);
        byOutput.values()
                .forEach(
                        list ->
                                list.sort(
                                        Comparator.comparing(Blueprint::id)
                                                .thenComparing(
                                                        b ->
                                                                b.pattern()
                                                                        .getDefinition()
                                                                        .toString())));
    }

    public static RecipeCatalog get(ServerLevel level) {
        return CACHED.computeIfAbsent(level.getServer(), s -> new RecipeCatalog(level));
    }

    public static RecipeCatalog get(ServerLevel level, Set<AEKey> components) {
        if (components.isEmpty()) return get(level);
        var cache = COMPONENT_CACHED.computeIfAbsent(level.getServer(), s -> new LinkedHashMap<>());
        var key = Set.copyOf(components);
        var result = cache.get(key);
        if (result != null) return result;
        try (var scope = new ComponentInputs(key)) {
            result = new RecipeCatalog(level);
        }
        var base = get(level);
        var componentCatalog = result;
        base.byOutput.forEach(
                (output, recipes) -> {
                    var merged = new LinkedHashMap<appeng.api.stacks.AEItemKey, Blueprint>();
                    recipes.forEach(r -> merged.put(r.pattern().getDefinition(), r));
                    componentCatalog
                            .byOutput
                            .getOrDefault(output, List.of())
                            .forEach(r -> merged.put(r.pattern().getDefinition(), r));
                    componentCatalog.byOutput.put(output, new ArrayList<>(merged.values()));
                });
        cache.put(key, result);
        while (cache.size() > 4) cache.remove(cache.keySet().iterator().next());
        return result;
    }

    public static void invalidate() {
        CACHED.clear();
        COMPONENT_CACHED.clear();
        VirtualPatterns.invalidate();
    }

    public List<Blueprint> forOutput(AEKey key) {
        return byOutput.getOrDefault(key, List.of());
    }

    public Map<String, String> unsupported() {
        return Collections.unmodifiableMap(unsupported);
    }
}
