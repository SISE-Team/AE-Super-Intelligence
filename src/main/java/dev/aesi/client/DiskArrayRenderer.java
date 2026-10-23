// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.client.render.AERenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aesi.item.DriveTiers;
import dev.aesi.multiblock.DiskArrayEntity;
import dev.aesi.multiblock.Workstation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Displays labels directly on the independent physical array, with no geometry in empty racks.
 */
public final class DiskArrayRenderer implements BlockEntityRenderer<DiskArrayEntity, DiskArrayRenderer.State> {
    private static final int[] TIER_COLORS = {0, 0x8D92A2, 0x689BC1, 0x97C5E3, 0xE8EAED};
    private static final int[] LED_COLORS = {0x606777, 0x7DA395, 0xCCC9A4};

    public static final class State extends BlockEntityRenderState {
        boolean visible, rear, powered;
        float rotation;
        int storey;
        final int[] tiers = new int[DiskArrayEntity.DRIVE_SLOTS], fills = new int[DiskArrayEntity.DRIVE_SLOTS];
        float brightness = 1;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(DiskArrayEntity array, State state, float partialTicks, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(array, state, partialTicks, cameraPos, crumblingOverlay);
        var blockState = array.getBlockState();
        state.visible = !Workstation.formed(blockState);
        if (!state.visible) return;
        int segment = blockState.getValue(Workstation.SEGMENT);
        state.storey = Workstation.layer(segment);
        state.rear = Workstation.rear(segment);
        state.rear = Workstation.formed(blockState) && state.rear;
        state.rotation = rotation(blockState.getValue(Workstation.FACING)) + (state.rear ? 180 : 0);
        var owner = Workstation.owner(array.getLevel(),array.getBlockPos(),blockState);
        state.powered = owner != null && owner.visualActivity() != 0;
        for (int i = 0; i < DiskArrayEntity.DRIVE_SLOTS; i++) {
            state.tiers[i] = array.visualTier(i);
            state.fills[i] = array.visualFill(i);
        }
        // The LED pipeline is unlit, so the bars are shaded by hand from the block's light.
        int block = state.lightCoords >> 4 & 15, sky = state.lightCoords >> 20 & 15;
        var level = array.getLevel();
        if (level != null) sky = Math.max(0, sky - level.getSkyDarken());
        state.brightness = 0.3f + 0.7f * Math.max(block, sky) / 15f;
    }

    private static float rotation(Direction facing) {
        return switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(DiskArrayEntity array) {
        return new net.minecraft.world.phys.AABB(array.getBlockPos()).inflate(.01);
    }

    @Override public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (!state.visible) return;
        boolean any = false;
        for (int tier : state.tiers) any |= tier > 0;
        if (!any) return;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.rotation));
        poseStack.translate(-0.5, -0.5, -0.5);
        nodes.submitCustomGeometry(poseStack, AERenderTypes.STORAGE_CELL_LEDS, (pose, consumer) -> {
            for (int i = 0; i < DiskArrayEntity.DRIVE_SLOTS; i++) {
                int tier = state.tiers[i];
                if (tier <= 0 || tier > DriveTiers.TIERS) continue;
                float x = 2 + 3 * i;
                rackBox(consumer, pose, state, x, 2, -.04f,
                        x + 1, 6, -.02f, TIER_COLORS[tier], state.brightness);
                if(state.powered) {
                    int fill = Math.clamp(state.fills[i], 0, LED_COLORS.length - 1);
                    rackBox(consumer, pose, state, x, 6, -.04f,
                            x + 1, 7, -.02f, LED_COLORS[fill], 1);
                }
            }
        });
        poseStack.popPose();
    }

    /** Source-model coordinates are transformed relative to this mount, not its owner. */
    private static void rackBox(VertexConsumer consumer, PoseStack.Pose pose, State state,
            float a0, float b0, float d0, float a1, float b1, float d1, int rgb, float light) {
        float x0 = a0, x1 = a1;
        float y0 = b0, y1 = b1;
        float z0 = d0, z1 = d1;
        x0 /= 16; x1 /= 16; y0 /= 16; y1 /= 16; z0 /= 16; z1 /= 16;
        float r = (rgb >> 16 & 255) / 255f * light, g = (rgb >> 8 & 255) / 255f * light, b = (rgb & 255) / 255f * light;
        quad(consumer, pose, r, g, b, 1.0f, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1); // top
        quad(consumer, pose, r, g, b, 0.5f, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0); // bottom
        quad(consumer, pose, r, g, b, 0.8f, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0); // north
        quad(consumer, pose, r, g, b, 0.8f, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1); // south
        quad(consumer, pose, r, g, b, 0.6f, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1); // west
        quad(consumer, pose, r, g, b, 0.6f, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0); // east
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose, float r, float g, float b, float shade,
            float... v) {
        for (int i = 0; i < 12; i += 3) c.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(r * shade, g * shade, b * shade, 1f);
        for (int i = 9; i >= 0; i -= 3) c.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(r * shade, g * shade, b * shade, 1f);
    }
}
