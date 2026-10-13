// SPDX-License-Identifier: MIT
package dev.aesi.api;

import dev.aesi.recipe.Blueprint;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/** Register via ServiceLoader; runs on the server thread, once per recipe generation. */
public interface RecipeAdapter {
    void read(ServerLevel level, List<Blueprint> output, Map<String, String> unsupported);
}
