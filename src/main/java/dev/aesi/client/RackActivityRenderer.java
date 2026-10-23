// SPDX-License-Identifier: MIT
package dev.aesi.client;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.client.render.AERenderTypes;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.aesi.controller.SmartController;
import dev.aesi.multiblock.Workstation;
import dev.aesi.multiblock.RackServers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.*;

/** Small pixel overlays driven by server activity; no always-running emissive texture or world tick work. */
public final class RackActivityRenderer implements BlockEntityRenderer<PatternProviderBlockEntity,RackActivityRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        boolean formed;
        int activity,rotation,computeMask,storageMask;
        long time;
        final net.minecraft.client.renderer.item.ItemStackRenderState compute=new net.minecraft.client.renderer.item.ItemStackRenderState();
        final net.minecraft.client.renderer.item.ItemStackRenderState storage=new net.minecraft.client.renderer.item.ItemStackRenderState();
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(PatternProviderBlockEntity entity,State state,float partialTicks,Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partialTicks,camera,overlay);
        state.formed=entity instanceof SmartController&&Workstation.formed(entity.getBlockState())&&entity.getBlockState().getValue(Workstation.TALL);
        if(!state.formed)return;
        state.activity=((SmartController)entity).visualActivity();
        state.computeMask=0;
        for(int i=0;i<dev.aesi.multiblock.ComputingArrayEntity.SEGMENTS.size();i++) {
            var pos=Workstation.position(entity.getBlockPos(),entity.getBlockState().getValue(Workstation.FACING),dev.aesi.multiblock.ComputingArrayEntity.SEGMENTS.get(i));
            if(entity.getLevel()!=null && entity.getLevel().getBlockEntity(pos) instanceof dev.aesi.multiblock.ComputingArrayEntity array)
                state.computeMask|=array.bladeMask()<<(i*4);
        }
        state.storageMask=0;
        for(int i=0;i<6;i++) {
            var pos=dev.aesi.multiblock.DiskArrayEntity.position(entity.getBlockPos(),entity.getBlockState().getValue(Workstation.FACING),i);
            if(entity.getLevel()!=null&&entity.getLevel().getBlockEntity(pos) instanceof dev.aesi.multiblock.DiskArrayEntity)state.storageMask|=1<<i;
        }
        var resolver=net.minecraft.client.Minecraft.getInstance().getItemModelResolver();
        resolver.updateForTopItem(state.compute,dev.aesi.Aesi.COMPUTE_SERVER.get().getDefaultInstance(),net.minecraft.world.item.ItemDisplayContext.NONE,entity.getLevel(),null,0);
        resolver.updateForTopItem(state.storage,dev.aesi.Aesi.PATTERN_DISK.get().getDefaultInstance(),net.minecraft.world.item.ItemDisplayContext.NONE,entity.getLevel(),null,0);
        state.time=entity.getLevel()==null?0:entity.getLevel().getGameTime();
        state.rotation=switch(entity.getBlockState().getValue(Workstation.FACING)){
            case EAST->90;case SOUTH->180;case WEST->270;default->0;
        };
    }
    @Override public AABB getRenderBoundingBox(PatternProviderBlockEntity entity){
        var head=entity.getBlockPos();
        if(!Workstation.formed(entity.getBlockState()))return new AABB(head);
        var end=Workstation.position(head,entity.getBlockState().getValue(Workstation.FACING),Workstation.BLOCK_COUNT-1);
        return new AABB(Math.min(head.getX(),end.getX()),head.getY(),Math.min(head.getZ(),end.getZ()),
                Math.max(head.getX(),end.getX())+1,head.getY()+Workstation.HEIGHT,Math.max(head.getZ(),end.getZ())+1).inflate(.01);
    }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector nodes,CameraRenderState camera){
        if(!state.formed)return;
        poses.pushPose();poses.translate(.5,.5,.5);poses.mulPose(Axis.YP.rotationDegrees(-state.rotation));poses.translate(-.5,-.5,-.5);
        for(int column=0;column<3;column++){
            for(int row=0;row<4;row++)if((state.computeMask&(1<<(column*4+row)))!=0)
                server(poses,nodes,state.compute,RackServers.x(true),RackServers.bottom(true,column,row),state.lightCoords);
            for(int row=0;row<2;row++)if((state.storageMask&(1<<(column*2+row)))!=0)
                server(poses,nodes,state.storage,RackServers.x(false),RackServers.bottom(false,column,row),state.lightCoords);
        }
        nodes.submitCustomGeometry(poses,AERenderTypes.STORAGE_CELL_LEDS,(pose,vertices)->{
            int mode=state.activity;
            if(mode==0)return;
            boolean busy=mode==2||mode==3;
            int phase=(int)(state.time/(busy?3:12)%8);
            // A recessed monitor face, aligned to the physical glass of the core block.
            float glass=.985f;
            pixel(vertices,pose,19,12,glass,10,1,0x425B68);
            pixel(vertices,pose,20,9,glass,4,1,0x788B94);
            pixel(vertices,pose,20,7,glass,3,1,0x607783);
            pixel(vertices,pose,26,5,glass,1,5,0x385867);
            pixel(vertices,pose,24,9,glass,3,1,0x385867);
            int progress=busy?1+phase:2;
            if(progress<8)pixel(vertices,pose,20+progress,4,glass,8-progress,1,0x263943);
            pixel(vertices,pose,20,4,glass,progress,1,busy?0x9AC5D5:0x597689);
            // Activity is attached to the installed machine, never to an empty construction bay.
            for(int i=0;i<12;i++)if((state.computeMask&(1<<i))!=0)
                pixel(vertices,pose,16*RackServers.x(true)+7,(float)RackServers.bottom(true,i/4,i%4)+1,-.015f,1,1,
                        busy&&(phase+i)%4<2?0x8EBCCB:0x344653);
            for(int i=0;i<6;i++)if((state.storageMask&(1<<i))!=0)
                pixel(vertices,pose,16*RackServers.x(false)+12,(float)RackServers.bottom(false,i/2,i%2)+1,-.015f,1,1,0x668D83);
        });
        poses.popPose();
    }
    private static void server(PoseStack poses,SubmitNodeCollector nodes,net.minecraft.client.renderer.item.ItemStackRenderState model,
            int column,double y,int light){
        var bounds=model.getModelBoundingBox();
        poses.pushPose();poses.translate(-column+1/16.0-bounds.minX,y/16.0-bounds.minY,-bounds.minZ);
        model.submit(poses,nodes,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);
        poses.popPose();
    }
    private static void fan(VertexConsumer out,PoseStack.Pose pose,int x,int y,float z,int phase){
        int[][] ring={{2,0},{3,1},{4,2},{3,3},{2,4},{1,3},{0,2},{1,1}};
        for(int i=0;i<2;i++){
            var p=ring[(phase+i*4)%8];pixel(out,pose,x+p[0],y+p[1],z,1,1,0x667B89);
        }
    }
    private static void pixel(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float w,float h,int color){
        float a=(16-x-w)/16,b=(16-x)/16,c=y/16,d=(y+h)/16,e=z/16;
        float r=(color>>16&255)/255f,g=(color>>8&255)/255f,blue=(color&255)/255f;
        float[] vertices={a,c,e,b,c,e,b,d,e,a,d,e};
        for(int i=0;i<12;i+=3)out.addVertex(pose,vertices[i],vertices[i+1],vertices[i+2]).setColor(r,g,blue,1);
        for(int i=9;i>=0;i-=3)out.addVertex(pose,vertices[i],vertices[i+1],vertices[i+2]).setColor(r,g,blue,1);
    }
}
