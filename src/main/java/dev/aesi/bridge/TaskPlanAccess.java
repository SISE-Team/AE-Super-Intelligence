// SPDX-License-Identifier: MIT
package dev.aesi.bridge;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import java.util.Collection;

public interface TaskPlanAccess {
    Collection<IPatternDetails> aesiSelect(
            AEKey output, Collection<IPatternDetails> nativeCandidates);
}
