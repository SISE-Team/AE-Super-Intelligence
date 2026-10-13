// SPDX-License-Identifier: MIT
package dev.aesi.recipe;

import appeng.api.crafting.IPatternDetails;

/** Server-normalized recipe and stable, native CPU-decodable pattern. */
public record Blueprint(
        String id,
        String family,
        IPatternDetails pattern,
        long batchTicks,
        boolean timingKnown,
        long requiredEu) {}
