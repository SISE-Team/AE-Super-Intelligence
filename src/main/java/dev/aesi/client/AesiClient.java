// SPDX-License-Identifier: MIT
package dev.aesi.client;

import dev.aesi.Aesi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.client.color.block.BlockTintSources;

@Mod(value = Aesi.ID, dist = Dist.CLIENT)
public final class AesiClient {
    public AesiClient(IEventBus bus) {
        bus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> {
            event.register(java.util.List.of(BlockTintSources.constant(0xFF8BABB9)),
                    Aesi.COMPUTE_SERVER_BLOCK.get(),Aesi.STRATEGY_ARRAY.get());
            event.register(java.util.List.of(BlockTintSources.constant(0xFF667580)),Aesi.DISK_ARRAY.get());
        });
        bus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(Aesi.WORKSTATION_MENU.get(), WorkstationScreen::new);
            event.register(Aesi.PATTERN_DRIVE_MENU.get(), PatternDriveScreen::new);
            event.register(Aesi.DRIVE_ARRAY_MENU.get(), DriveArrayScreen::new);
            event.register(Aesi.COMPUTE_ARRAY_MENU.get(), ComputeArrayScreen::new);
            event.register(Aesi.SMART_PATTERN_MENU.get(), SmartPatternScreen::new);
        });
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(Aesi.DISK_ARRAY_ENTITY.get(), context -> new DiskArrayRenderer());
            event.registerBlockEntityRenderer(Aesi.CONTROLLER_ENTITY.get(), context -> new RackActivityRenderer());
        });
    }
}
