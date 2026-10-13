# AE Super Intelligence (AESI)

AE Super Intelligence extends Applied Energistics 2 with smart target patterns. Place a target pattern in the Smart Crafting Controller, and AESI discovers supported recipes and machines on the ME network. AE2's own terminals, crafting CPUs, material accounting, progress tracking and cancellation execute the resulting plan.

Current version: **0.2.0-beta**.

## Requirements

- Minecraft **26.1.2**
- NeoForge **26.1.2.114**
- Java **25**
- Applied Energistics 2 **26.1.13-beta**

Industrial integrations are optional at runtime. Chemical resources require Mekanism and Applied Mekanistics. The exact API versions used for compilation and compatibility verification are pinned in [dependencies.lock.json](dependencies.lock.json).

## Usage

1. Craft a Smart Pattern from a Blank Pattern and a Logic Processor. Hold the target item or a fluid/chemical container in your other hand and use the Smart Pattern to record the target. Sneak-use cycles between balanced, speed and material-saving strategies.
2. Connect a Smart Crafting Controller to the ME network and place the target pattern in its pattern inventory.
3. Connect Molecular Assemblers and supported industrial machines through pattern providers, interfaces or export buses. Machines need valid input access and their usual energy, fuel and structure conditions.
4. Order the target from an existing ME terminal, preview the materials and select an existing crafting CPU. Machine-side providers can remain empty of physical patterns for these smart steps.

Ordinary patterns can participate in the same plan. Provider redstone controls, blocking and native buffers continue to apply. Configure a wait-for-result lock on the provider connected to the actual machine; the Smart Crafting Controller itself does not support that lock. Direct smart dispatch through a pulse-mode export bus is unsupported.

Administrators can inspect a controller with `/aesi status <x> <y> <z>`.

## Supported integrations

| Integration | Supported operations |
| --- | --- |
| Vanilla / AE2 | Deterministic shaped and shapeless recipes up to 3×3; furnace, smoker and blast furnace; native Molecular Assembler execution |
| Modern Industrialization | Known crafting machines with multiple item/fluid inputs and outputs; tier, power, mode and output checks |
| Mekanism / Applied Mekanistics | Fixed-consumption item, fluid and chemical processing, deterministic byproducts and factories using the same protocols |
| IndustrialCraft2 CE | Macerating, extraction, compression, ore washing, thermal centrifuging and metal-forming modes |
| Industrial Foregoing | Dissolution Chamber and the defined latex-processing protocol, including rubber-to-plastic chains |
| Ender IO | Alloy Smelter modes and deterministic SAG Mill recipes without optional random outputs or grinding-ball modifiers |
| Oritech | Known assembler, pulverizer, foundry, refinery, centrifuge, cooling and electric furnace protocols with actual structure checks |

Compatibility has been exercised with native and installed ExtendedAE, ExtendedAE Plus, AdvancedAE, AE2CS and AE2LT providers; native, Plus, AdvancedAE quantum and NeoECO CPUs; and native, wireless, universal and dedicated AE2 terminals. This covers the pinned installed versions and their actual machine protocols.

## Resource handling

AESI preserves NBT/data components, custom names, nested data and numeric types. It chooses actual inventory variants that satisfy the recipe's ingredient predicates. Distinct component-bearing resources retain separate identities; data is never stripped to force a match.

When a machine has automatic eject disabled, AESI collects items, fluids and chemicals from output slots or tanks explicitly recognized by its adapter. Collection uses transactions and the existing persistent AE2 return buffer. A full buffer leaves the output in the machine, and a rejected transfer rolls extraction back. Input resources, catalysts, fuel, capacitors and infusion material are excluded from recovery.

Scheduling can move work between machines with the same input/output contract. Running tasks retain their recipe definitions; a changed processing contract pauses dispatch. Unknown machines produce diagnostics rather than inferred recipes.

Random or weighted outputs, upgrade-dependent per-tick chemical consumption, complex catalyst loops and world/entity-dependent processes are outside automatic planning. Native physical patterns remain available for these processes. Large-network performance benchmarks and joint optimization across multiple target recipes are outside this beta's validated scope.

## Build

The Gradle build reads APIs from an existing Minecraft instance. It does not download, modify or redistribute Minecraft or third-party mod JARs. Supply an instance containing `mods/` and `libraries/` with the exact pinned APIs. Embedded APIs from Ender IO and NeoForge are extracted into the local build directory for compilation only.

Set `JAVA_HOME` to a Java 25 JDK, then run from the repository root:

```powershell
.\gradlew.bat -PaesiInstance="C:/path/to/minecraft-instance" build
```

On Linux or macOS:

```sh
./gradlew -PaesiInstance="/path/to/minecraft-instance" build
```

`AESI_INSTANCE_DIR` can be used instead of `-PaesiInstance`. In a checkout under an instance's `.work/` directory, the build can detect the instance two directories above the repository.

Output:

- `build/libs/AESI-26.1.2-0.2.0-beta.jar`
- `build/libs/AESI-26.1.2-0.2.0-beta-sources.jar`

`build` also runs the standalone planner checks through `plannerTest`. They cover shared inventory, surplus reuse, alternative inputs, cycles, parallel machine timing, search budgets, component identity, large-integer costs and an independent small-graph oracle. Run them separately with:

```sh
./gradlew -PaesiInstance="/path/to/minecraft-instance" plannerTest
```

The production JAR contains neither test classes nor third-party dependencies.

## License

AESI's source code and resources are licensed under the [MIT License](LICENSE). The Gradle wrapper retains its own license and notices under `gradle/wrapper/`; Minecraft and third-party dependencies retain their respective licenses.
