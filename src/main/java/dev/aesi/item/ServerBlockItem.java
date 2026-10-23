// SPDX-License-Identifier: MIT
package dev.aesi.item;

import dev.aesi.multiblock.ServerBody;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Both occupied cells participate in NeoForge's native placement snapshot transaction. */
public class ServerBlockItem extends BlockItem {
    protected ServerBlockItem(Block block,Properties properties){super(block,properties);}
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state){
        return super.canPlace(context,state)&&ServerBody.canPlace(context,state);
    }
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){
        if(!ServerBody.canPlace(context,state)||!super.placeBlock(context,state))return false;
        return ServerBody.attach(context.getLevel(),context.getClickedPos(),state);
    }
}
