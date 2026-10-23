// SPDX-License-Identifier: MIT
package dev.aesi.item;

/** Pure tier and paging math for pattern drives and drive arrays. */
public final class DriveTiers {
    public static final int TIERS = 4;
    public static final int SLOTS = 4;
    public static final int ARRAY_MAX = 256;
    public static final int EMPTY = 0, SOME = 1, FULL = 2;

    private DriveTiers() {}

    public static int capacity(int tier) {
        return tier <= 0 || tier > TIERS ? 0 : 8 << (tier - 1);
    }

    public static int total(int... tiers) {
        int sum = 0;
        for (int tier : tiers) sum += capacity(tier);
        return sum;
    }

    public static int fill(int used, int cap) {
        if (used <= 0) return EMPTY;
        return used >= cap ? FULL : SOME;
    }

    public static int pages(int size, int pageSize) {
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    public static int clampPage(int page, int size, int pageSize) {
        return Math.max(0, Math.min(page, pages(size, pageSize) - 1));
    }

    public static int upgradeTarget(int tier) {
        return tier >= 1 && tier < TIERS ? tier + 1 : 0;
    }
}
