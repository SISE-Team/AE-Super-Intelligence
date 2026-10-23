# AE Super Intelligence (AESI)

AE Super Intelligence extends Applied Energistics 2 with smart target patterns. Place a target pattern in the Smart Crafting Controller, and AESI discovers supported recipes and machines on the ME network. AE2's own terminals, crafting CPUs, material accounting, progress tracking and cancellation execute the resulting plan.

Current version: **0.3.0-beta**.

## Requirements

- Minecraft **26.1.2**
- NeoForge **26.1.2.114**
- Java **25**
- Applied Energistics 2 **26.1.13-beta**

Industrial integrations are optional at runtime. Chemical resources require Mekanism and Applied Mekanistics. The exact API versions used for compilation and compatibility verification are pinned in [dependencies.lock.json](dependencies.lock.json).

## Usage

1. Craft a Smart Pattern from a Blank Pattern and a Logic Processor. Hold the target item or a fluid/chemical container in your other hand and use the Smart Pattern to record the target. With an empty offhand, right-click to open the target editor; the sample slot is a non-consuming ghost slot. Sneak-use cycles between balanced, speed and material-saving strategies.
2. Build the 5脳3脳2 Planning Workstation described below. Connect the back of its ME Network Port to the network, then right-click the machine and place the target pattern in its pattern inventory.
3. Prepare the required process-dataset carriers from Blank Modules, AE2 Blank Patterns and their exact listed ingredients, then record them using the specified native machines. Install each completed disk in a 2U Computing Server, then insert the server into the cabinet. Three columns hold four independent computing servers each, for twelve dataset slots. Every virtual recipe, including intermediate steps, requires its exact process and voltage range. An arbitrary mod sample unlocks nothing; duplicate grants do not stack.
4. Connect Molecular Assemblers and supported industrial machines through pattern providers, interfaces or export buses. Machines need valid input access and their usual energy, fuel and structure conditions.
5. Order the target from an existing ME terminal, preview the materials and select an existing crafting CPU. Machine-side providers can remain empty of physical patterns for these smart steps.

Ordinary patterns can participate in the same plan. Provider redstone controls, blocking and native buffers continue to apply. Configure a wait-for-result lock on the provider connected to the actual machine; the Smart Crafting Controller itself does not support that lock. Direct smart dispatch through a pulse-mode export bus is unsupported.

Removing a required upgrade immediately withdraws the affected virtual recipes and prevents further dispatch, including from an already submitted job. AE2 retains the job; reinstalling the component permits the existing recipe contract to resume. Ordinary hand-encoded AE2 patterns remain available. Components unlock supported recipes; they do not add adapters for unknown machine protocols.

Administrators can inspect a controller with `/aesi status <x> <y> <z>`.

## Process datasets and industrial suites

The 93 registered datasets describe finite process permissions, not whole-mod namespaces. A compressor disk authorizes compression only, regardless of which mod supplied the recipe or its output. Workbench knowledge covers ordinary 3x3 crafting only. Existing stock may still be consumed without knowing how to manufacture it. The system continues to plan routes and generate virtual patterns automatically; disks supply knowledge, not machines, power, channels or recipe inputs.

Each process has a separate **unwritten carrier** and **recorded disk**. Carriers grant no access. JEI shows the normal workbench preparation recipe followed by the mod's own recording recipe. Industrial suites consume their prerequisite disks and are completed by native machinery. Larger suites use intermediate groups to fit a 3x3 workbench; they contain an explicit union of learned processes, never a namespace wildcard.

| Dataset family | Native recording process |
| --- | --- |
| Workbench, smelting, blasting, smoking | Workbench and the matching vanilla furnace; AE precision assembly uses the Inscriber |
| Modern Industrialization | Matching compression, wire-milling, mixing, chemistry and other machines; fluid-only processes supply their named products to an MI Assembler |
| Mekanism | Matching item-processing machines; chemical/fluid processes supply their specified products to a Pressurized Reaction Chamber; the complete suite uses antimatter in a Nucleosynthesizer |
| IndustrialCraft2 CE | Macerator, Extractor, Compressor, Ore Washing Plant, Thermal Centrifuge and the three Metal Former modes |
| Industrial Foregoing | Dissolution Chamber with latex, pink slime or ether gas; latex knowledge also consumes the native rubber product |
| Ender IO | Alloy Smelter and SAG Mill; recording disables grinding-ball output multiplication |
| Oritech | Assembler, Pulverizer, Foundry, Refinery and Centrifuge; cooling knowledge consumes ice and a native chiller, then records in the Assembler because the chiller has no item input |

MI basic disks cover up to **32 EU/t**, advanced disks up to **512 EU/t**. The advanced suite consumes the basic suite plus its named advanced process groups and is assembled at 512 EU/t. The complete suite consumes that advanced suite and the fusion dataset; recording needs an 8192 EU/t assembler and helium plasma. The machine performing a planned recipe must still support the actual required tier. The MI furnace protocol is recorded in an assembler from an existing smelting disk and an electric furnace.

Old sample-bound Mod Upgrade Components are inert and can be recycled into Blank Modules. They cannot retain whole-mod permission through saved custom data. Optional integrations only enable their recipes and disks when the corresponding mod is loaded. Dataset definitions are shipped with the mod; native acquisition recipes are ordinary data-pack recipes. No new automatic adapter for AdvancedAE reaction chambers, altars or unknown machines is implied by these datasets.

## Planning Workstation

The workstation is a compact, elongated structure made from **30 actual blocks**. Place the controller facing you at the front-left bottom corner. Each row below runs left to right as you face the controller. The first row is the front; the second is the back.

| Layer | Front row | Back row |
| --- | --- | --- |
| Top | Compute rack / Storage rack | Cooler / Storage rack |
| Middle two layers | Compute rack / Storage rack | Casing / Storage rack |
| Bottom | Controller / Monitor core | Port / Bus |

Required components: **1 controller, 1 ME port, 1 planning core, 1 data bus, 9 empty array racks, 1 cooling module and 2 casings**. All have crafting recipes and appear in the Redstone creative tab. Run `/aesi layout` in game for localized assembly instructions; no operator permission is needed.

An intact previous 5 × 3 × 2 rack relocates into the 2 × 4 × 2 layout only when the new footprint is clear and loaded. It preserves each storage-bank index, blade mask and dataset, returning the fourteen surplus construction components. Obstructed, partial or foreign structures are not rewritten. The SISE Core wand reads the current layout and preserves installed machines during continuation.

The controller chooses the orientation; the other components align automatically. All four horizontal directions work. The structure forms automatically within half a second after the last block is placed. The single fixed ME port is a required construction block directly behind the controller in the bottom layer. Only its exterior rear face connects to a cable; this one connection supplies both AE power and channels for the whole workstation. No array or core needs a separate connection. The port uses no additional channel; the controller retains AE2's normal power and channel requirements. Right-click any formed component to open the workstation's dedicated pattern and array interface.

Breaking a required construction component or unloading a required chunk suspends new smart planning and dispatch. Rebuilding resumes operation without copying inventories or changing running recipe contracts. The controller retains its target patterns and return buffer; breaking the controller drops its stored items through AE2's existing logic. Mounted Disk Arrays own their drives and Computing Arrays own their upgrade components. Breaking or wrench-removing either carries its contents with that array. An already submitted job stays with its native AE2 CPU and may finish work already dispatched; cancel it through AE2 if you no longer intend to repair the machine. AESI never force-loads chunks.

Version 0.3 replaces the standalone controller with this multiblock. An existing controller keeps its inventory and becomes operational after the surrounding structure is built. Formation creates only the white cabinet, rails and opaque backplanes. Servers are installed separately. The two cabinets each have **24U** in three equipment blocks above the base: twelve **2U Computing Servers** in one cabinet and six **4U Storage Servers** in the other. Each construction block contains an 8U opening with a one-pixel frame above and below: fourteen usable model pixels. A 2U computing server is 3.5 pixels high and a 4U storage server is seven, preserving their exact 1:2 ratio. Each server’s height fits wholly inside its own vertical block; types never share a cell or cross its vertical boundary. The physical body is two blocks deep, including when placed outside the cabinet. Both cells reserve space and have selectable collision; breaking or wrenching either end returns exactly one server with its contents. The complete cabinet holds twelve computing servers and six storage servers. Every server uses the same independent 3D geometry in hand, as a two-cell loose machine and when installed. The rear section holds no duplicate inventory. All AESI screens use AE2’s native panel generator, slot atlas, tabs and buttons.

Aim at the desired front slot and right-click with the matching server to install it. **Sneak and right-click with both hands empty to remove exactly the server under the cursor.** Its dataset or drives travel with it. Removing the last server leaves the cabinet intact and empty; installation never comes from formation alone. Each computing server grants only its installed process dataset. Legacy four-server array bundles retain their contents and occupied-slot mask.

The storage-cabinet base core is a complete monitor block with a deep case, bezel and recessed display. One independent cooling block occupies the computing cabinet’s upper rear, with louvers facing backward. The continuous white outer frame uses the original native 16-pixel material palette and per-face UVs. Computing fronts are muted blue-gray with silver handles; storage frames are graphite-gray. Continuous posts and per-cell rails frame both cabinets, with each removable server recessed to the same front plane. Solid backplanes close empty slots. The one rear ME inlet supplies the entire cabinet. Small pixel animations show monitor and installed-server activity only while the complete structure has an active, powered ME network. Disconnecting the inlet, losing power/channels or breaking the structure stops these animations. No upstream textures or models are copied. The cabinet renders in normal chunk sections; its head renders only the servers actually installed. Crafting remains with the existing AE2 CPU.

## Disk Arrays

The 2 x 4 x 2 host has nine built-in pattern slots. Its front has six **4U Storage Server** slots: two per equipment block in the storage cabinet. Each storage server holds four removable pattern drives. Basic, Advanced, Elite and Quantum drives hold **8, 16, 32 and 64 patterns** respectively. Six servers with four Quantum drives each provide **1,545 target slots**, including the host. The cabinet stays 2 x 4 x 2 at every expansion level. Installing consumes one portable server and retains the construction rack. Removing it restores the empty slot without duplicating its contents. Previously placed external arrays retain their contents and can be retrieved with an AE2 wrench and installed in these bays.

Right-click the host or a mounted array to open the pattern screen. Six status indicators show which mounts are occupied. Pattern pages retain stable slot ownership; unavailable mounts reject insertion. Arrays are optional and excluded from the multiblock wand's host material cost.

Each array owns and saves its drives; each drive owns its patterns. Right-click a held array to manage its four drives, or a held drive to edit its paginated pattern inventory. Drive tier upgrades preserve stored patterns. Breaking or wrench-removing it carries those patterns inside the dropped array; replacing it in another mount restores them. Breaking the host does not drop or clear adjacent arrays. Removing an array withdraws its targets from future planning; already submitted work remains owned by AE2's CPU.

Older internally installed modules remain available in the screen's withdrawal slots. Withdraw them and place them as Disk Array blocks; their stored patterns are retained. Craft a Disk Array with iron ingots, logic/calculation processors, a blank pattern and redstone.

## Supported integrations

| Integration | Supported operations |
| --- | --- |
| Vanilla / AE2 | Deterministic shaped and shapeless recipes up to 3脳3; furnace, smoker and blast furnace; native Molecular Assembler execution |
| Modern Industrialization | Known crafting machines with multiple item/fluid inputs and outputs; tier, power, mode and output checks |
| Mekanism / Applied Mekanistics | Fixed-consumption item, fluid and chemical processing, deterministic byproducts and factories using the same protocols |
| IndustrialCraft2 CE | Macerating, extraction, compression, ore washing, thermal centrifuging and metal-forming modes |
| Industrial Foregoing | Dissolution Chamber and the defined latex-processing protocol, including rubber-to-plastic chains |
| Ender IO | Alloy Smelter modes and deterministic SAG Mill recipes without optional random outputs or grinding-ball modifiers |
| Oritech | Known assembler, pulverizer, foundry, refinery, centrifuge, cooling and electric furnace protocols with actual structure checks |

The 0.2 compatibility suite exercised native and installed ExtendedAE, ExtendedAE Plus, AdvancedAE, AE2CS and AE2LT providers; native, Plus, AdvancedAE quantum and NeoECO CPUs; and native, wireless, universal and dedicated AE2 terminals. Version 0.3 updates the build pins to the current installed APIs and the renamed IC2-CE interfaces. Its validation focuses on the multiblock lifecycle, native AE2 crafting, interruption/restart and client models; the complete industrial matrix has not been rerun against every updated dependency.

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

- `build/libs/AESI-26.1.2-0.3.0-beta.jar`
- `build/libs/AESI-26.1.2-0.3.0-beta-sources.jar`

`build` also runs the standalone planner checks through `plannerTest`. They cover shared inventory, surplus reuse, alternative inputs, cycles, parallel machine timing, search budgets, component identity, large-integer costs and an independent small-graph oracle. Run them separately with:

```sh
./gradlew -PaesiInstance="/path/to/minecraft-instance" plannerTest
```

The production JAR contains neither test classes nor third-party dependencies.

## License

AESI's source code and resources are licensed under the [MIT License](LICENSE). The Gradle wrapper retains its own license and notices under `gradle/wrapper/`; Minecraft and third-party dependencies retain their respective licenses.
