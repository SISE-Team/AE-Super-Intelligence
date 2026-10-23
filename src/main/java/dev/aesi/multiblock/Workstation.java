// SPDX-License-Identifier: MIT
package dev.aesi.multiblock;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.menu.locator.MenuLocators;
import dev.aesi.Aesi;
import dev.aesi.controller.SmartController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Fixed 2 x 4 x 2 layout, viewed from the controller's front. No chunk tickets. */
public final class Workstation {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final int WIDTH = 2, HEIGHT = 4, DEPTH = 2;
    public static final int BLOCK_COUNT = WIDTH * HEIGHT * DEPTH;
    // Missing in older saves; keeps their ownership coordinates valid until safe relocation.
    public static final BooleanProperty TALL = BooleanProperty.create("tall");
    public static final int CORE_SEGMENT = 1;
    public static final int NETWORK_PORT_SEGMENT = 2;
    public static final List<Integer> STORAGE_SEGMENTS = List.of(5,7,9,11,13,15);
    private static final ThreadLocal<Boolean> RELOCATING = ThreadLocal.withInitial(()->false);
    public static boolean relocating() { return RELOCATING.get(); }
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 29);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Rotation of an assembled tile would detach it from its owner. Place the head facing
    // the desired direction instead; wrench disassembly remains an AE2 operation.
    public static final IOrientationStrategy ORIENTATION = new IOrientationStrategy() {
        public Collection<Property<?>> getProperties() { return List.of(FACING); }
        public Direction getFacing(BlockState state) { return state.getValue(FACING); }
        public BlockState setFacing(BlockState state, Direction facing) {
            return facing.getAxis().isHorizontal() ? state.setValue(FACING, facing) : state;
        }
        public BlockState getStateForPlacement(BlockState state, BlockPlaceContext context) {
            return OrientationStrategies.horizontalFacing().getStateForPlacement(state, context);
        }
        public boolean allowsPlayerRotation() { return false; }
    };
    public enum Part { CONSOLE, BUS, RACK, PORT, CASING, CORE, COOLING }
    public static final List<Part> LAYOUT = List.of(
            Part.CONSOLE, Part.CORE, Part.PORT, Part.BUS,
            Part.RACK, Part.RACK, Part.CASING, Part.RACK,
            Part.RACK, Part.RACK, Part.CASING, Part.RACK,
            Part.RACK, Part.RACK, Part.COOLING, Part.RACK);

    private Workstation() {}

    public static Block block(int segment) {
        return switch (LAYOUT.get(segment)) {
            case CONSOLE -> Aesi.CONTROLLER.get();
            case BUS -> Aesi.DATA_BUS.get();
            case RACK -> Aesi.ARRAY_RACK.get();
            case PORT -> Aesi.NETWORK_PORT.get();
            case CASING -> Aesi.STRUCTURAL_CASING.get();
            case CORE -> Aesi.PLANNING_CORE.get();
            case COOLING -> Aesi.COOLING_UNIT.get();
        };
    }

    public static BlockPos offset(Direction facing, int segment) {
        return BlockPos.ZERO.relative(facing.getCounterClockWise(), segment % WIDTH)
                .above(segment / (WIDTH * DEPTH)).relative(facing.getOpposite(), (segment / WIDTH) % DEPTH);
    }

    public static int layer(int segment) { return segment / (WIDTH * DEPTH); }
    public static boolean rear(int segment) { return (segment / WIDTH) % DEPTH == 1; }
    public static BlockPos legacyOffset(Direction facing,int segment) {
        return BlockPos.ZERO.relative(facing.getCounterClockWise(),segment%5).above(segment/10)
                .relative(facing.getOpposite(),(segment/5)%2);
    }
    public static BlockPos position(BlockPos head, Direction facing, int segment) {
        return head.offset(offset(facing, segment));
    }

    public static boolean formed(BlockState state) {
        return state.hasProperty(FORMED) && state.getValue(FORMED);
    }

    public static boolean storageBay(int segment) { return STORAGE_SEGMENTS.contains(segment); }
    public static boolean computeBay(int segment) { return ComputingArrayEntity.SEGMENTS.contains(segment); }
    /** Array machines are optional occupants; the empty construction rack is sufficient to form. */
    public static boolean matches(int segment, BlockState state) {
        return segment >= 0 && segment < BLOCK_COUNT && (state.is(block(segment))
                || storageBay(segment) && (state.is(Aesi.DISK_ARRAY.get()) || state.is(Aesi.COOLING_UNIT.get()))
                || computeBay(segment) && state.is(Aesi.STRATEGY_ARRAY.get()));
    }

    public static BlockPos ownerPosition(BlockPos pos, BlockState state) {
        return pos.subtract(state.getValue(TALL) ? offset(state.getValue(FACING), state.getValue(SEGMENT))
                : legacyOffset(state.getValue(FACING), state.getValue(SEGMENT)));
    }

    public static SmartController owner(Level level, BlockPos pos, BlockState state) {
        if (!formed(state)) return null;
        var head = ownerPosition(pos, state);
        return level.hasChunkAt(head) && level.getBlockEntity(head) instanceof SmartController controller
                ? controller : null;
    }

    /** Empty means every tile is loaded, the right type, and not owned by another head. */
    public static List<Component> problems(SmartController controller, boolean requireFormed) {
        var level = controller.getLevel();
        if (level == null || controller.isRemoved()) return List.of(Component.translatable("message.aesi.unloaded"));
        var head = controller.getBlockPos();
        var facing = controller.getBlockState().getValue(FACING);
        var errors = new ArrayList<Component>();
        for (int i = 0; i < BLOCK_COUNT; i++) {
            var pos = position(head, facing, i);
            if (!level.hasChunkAt(pos)) {
                errors.add(Component.translatable("message.aesi.unloaded_at", pos.toShortString()));
                continue;
            }
            var state = level.getBlockState(pos);
            if (!matches(i, state)) {
                errors.add(Component.translatable("message.aesi.missing", pos.toShortString(), block(i).getName()));
            } else if (formed(state) && (!state.getValue(TALL) || state.getValue(SEGMENT) != i || state.getValue(FACING) != facing)) {
                errors.add(Component.translatable("message.aesi.occupied", pos.toShortString()));
            } else if (requireFormed && !formed(state)) {
                errors.add(Component.translatable("message.aesi.incomplete"));
            }
        }
        return errors;
    }

    public static void validate(SmartController controller) {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        LegacyRackLayout.upgrade(controller);
        if (formed(controller.getBlockState()) && !controller.getBlockState().getValue(TALL)) return;
        if (!problems(controller, false).isEmpty()) {
            disassemble(level, controller.getBlockPos(), controller.getBlockState().getValue(FACING));
            return;
        }
        var facing = controller.getBlockState().getValue(FACING);
        // Set the head last, so the network service cannot expose a half-built structure.
        for (int i = BLOCK_COUNT - 1; i >= 0; i--) {
            var pos = position(controller.getBlockPos(), facing, i);
            var old = level.getBlockState(pos);
            var updated = old.setValue(TALL,true).setValue(FORMED, true).setValue(SEGMENT, i).setValue(FACING, facing);
            if (old != updated) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
                if (updated.getBlock() instanceof WorkstationPartBlock)
                    level.scheduleTick(pos, updated.getBlock(), 20);
            }
        }
    }

    public static void disassemble(ServerLevel level, BlockPos head, Direction facing) {
        disassemble(level,head,facing,level.getBlockState(head).hasProperty(TALL)&&level.getBlockState(head).getValue(TALL));
    }
    private static void disassemble(ServerLevel level,BlockPos head,Direction facing,boolean tall) {
        // Disconnect the head first. Never erase items, saved contracts, or other owners.
        for (int i = 0; i < (tall?BLOCK_COUNT:30); i++) {
            var pos = head.offset(tall?offset(facing,i):legacyOffset(facing,i));
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (formed(state) && ownerPosition(pos, state).equals(head))
                level.setBlock(pos, state.setValue(FORMED, false).setValue(SEGMENT, 0), Block.UPDATE_CLIENTS);
        }
    }

    public static void removed(BlockState state, ServerLevel level, BlockPos pos) {
        if(relocating())return;
        if (formed(state) && !matches(state.getValue(SEGMENT), level.getBlockState(pos)))
            disassemble(level, ownerPosition(pos, state), state.getValue(FACING),state.getValue(TALL));
    }
    static void relocate(Runnable action) {
        RELOCATING.set(true);
        try {action.run();} finally {RELOCATING.remove();}
    }

    public static void checkOrphan(ServerLevel level, BlockPos pos, BlockState state) {
        if (formed(state)) {
            var head = ownerPosition(pos, state);
            // An unloaded owner is not an orphan. The controller revalidates before working.
            if (level.hasChunkAt(head) && !(level.getBlockEntity(head) instanceof SmartController))
                disassemble(level, head, state.getValue(FACING),state.getValue(TALL));
        }
    }

    public static InteractionResult use(Level level, BlockPos pos, BlockState state, Player player) {
        var controller = level.getBlockEntity(pos) instanceof SmartController c ? c : owner(level, pos, state);
        if (controller == null) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.aesi.assemble"));
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            validate(controller);
            var errors = problems(controller, true);
            if (!errors.isEmpty()) player.sendSystemMessage(errors.getFirst());
            controller.openMenu(player, MenuLocators.forBlockEntity(controller));
        }
        return InteractionResult.SUCCESS;
    }
}
