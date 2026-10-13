// SPDX-License-Identifier: MIT
package dev.aesi.planner;

import java.util.*;

/** Immutable, world-free planning inputs. K must preserve the resource's complete identity. */
public final class Model {
    private Model() {}

    public record Amount<K>(K key, long count) {
        public Amount {
            Objects.requireNonNull(key);
            if (count <= 0) throw new IllegalArgumentException("count");
        }
    }

    public record Ingredient<K>(List<Amount<K>> alternatives) {
        public Ingredient {
            alternatives = List.copyOf(alternatives);
            if (alternatives.isEmpty()) throw new IllegalArgumentException("alternatives");
        }
    }

    public record Slot(String machine, long readyTick, long batchTicks, boolean timeKnown) {
        public Slot {
            Objects.requireNonNull(machine);
            if (readyTick < 0 || batchTicks <= 0) throw new IllegalArgumentException("timing");
        }
    }

    public record Recipe<K>(
            String id, List<Ingredient<K>> inputs, List<Amount<K>> outputs, List<Slot> slots) {
        public Recipe {
            Objects.requireNonNull(id);
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            slots = List.copyOf(slots);
            if (outputs.isEmpty() || inputs.isEmpty())
                throw new IllegalArgumentException("nonempty recipe");
        }

        public Amount<K> output() {
            return outputs.getFirst();
        }
    }

    public record Snapshot<K>(
            Map<K, Long> inventory,
            List<Recipe<K>> recipes,
            Strategy strategy,
            int searchBudget,
            Map<K, java.math.BigInteger> materialCosts,
            java.math.BigInteger costUnit) {
        public Snapshot(
                Map<K, Long> inventory,
                List<Recipe<K>> recipes,
                Strategy strategy,
                int searchBudget) {
            this(inventory, recipes, strategy, searchBudget, Map.of(), java.math.BigInteger.ONE);
        }

        public Snapshot {
            inventory = Map.copyOf(inventory);
            recipes = List.copyOf(recipes);
            Objects.requireNonNull(strategy);
            materialCosts = Map.copyOf(materialCosts);
            Objects.requireNonNull(costUnit);
            if (costUnit.signum() <= 0
                    || materialCosts.values().stream().anyMatch(v -> v.signum() <= 0))
                throw new IllegalArgumentException("cost units");
            if (searchBudget < 1 || inventory.values().stream().anyMatch(n -> n < 0))
                throw new IllegalArgumentException("snapshot");
        }
    }
}
