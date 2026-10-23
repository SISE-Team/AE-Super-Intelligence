// SPDX-License-Identifier: MIT
package dev.aesi.item;

import static dev.aesi.item.DriveTiers.*;

public final class DriveTierTests {
    private static int checks;

    private static void require(boolean b, String msg) {
        if (!b) throw new AssertionError(msg);
        checks++;
    }

    public static void main(String[] args) {
        int[] expected = {0, 8, 16, 32, 64, 0};
        for (int tier = 0; tier <= 5; tier++)
            require(capacity(tier) == expected[tier], "capacity of tier " + tier);
        require(capacity(-1) == 0, "negative tier");
        require(total(4, 4, 4, 4) == ARRAY_MAX, "full quantum array");
        require(total(1, 0, 3, 0) == 40, "mixed array with empty slots");
        require(total() == 0, "no drives");
        require(fill(0, 8) == EMPTY, "empty drive");
        require(fill(1, 8) == SOME, "partly used drive");
        require(fill(7, 8) == SOME, "one slot left");
        require(fill(8, 8) == FULL, "full drive");
        require(pages(9 + 6 * ARRAY_MAX, 45) == 35, "controller pages");
        require(pages(0, 45) == 1, "at least one page");
        require(pages(45, 45) == 1 && pages(46, 45) == 2, "page boundary");
        require(clampPage(34, 57, 45) == 1, "clamp after removal");
        require(clampPage(-3, 57, 45) == 0, "clamp negative");
        require(pages(64, 36) == 2, "quantum drive pages");
        for (int tier = 1; tier < TIERS; tier++)
            require(upgradeTarget(tier) == tier + 1, "upgrade from " + tier);
        require(upgradeTarget(4) == 0 && upgradeTarget(0) == 0, "no upgrade");
        System.out.println(checks + " assertions passed");
    }
}
