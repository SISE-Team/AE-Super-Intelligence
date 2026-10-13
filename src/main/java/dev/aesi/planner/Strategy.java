// SPDX-License-Identifier: MIT
package dev.aesi.planner;

public enum Strategy {
    BALANCED,
    SPEED,
    MATERIALS;

    public Strategy next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
