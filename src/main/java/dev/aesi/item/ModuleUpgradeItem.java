// SPDX-License-Identifier: MIT
package dev.aesi.item;
import dev.aesi.Aesi;
import dev.aesi.recipe.LearningDatasets;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
/** Registered disk identity determines permissions; legacy sample cards grant nothing. */
public final class ModuleUpgradeItem extends Item {
    private final String dataset;
    public ModuleUpgradeItem(Properties properties){this(properties,"");}
    public ModuleUpgradeItem(Properties properties,String dataset){super(properties);this.dataset=dataset;}
    public static String module(ItemStack stack){
        if(!(stack.getItem() instanceof ModuleUpgradeItem card))return "";
        var entry=LearningDatasets.get(card.dataset);
        return entry!=null&&entry.loaded()?entry.id():"";
    }
    public static ItemStack forDataset(String id){
        var item=Aesi.DATASET_ITEMS.get(id);
        if(item==null)throw new IllegalArgumentException("Unknown process dataset "+id);
        return item.get().getDefaultInstance();
    }
    public static Component name(String id){return Component.translatable("item.aesi.dataset_"+id);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,
            Consumer<Component> lines,TooltipFlag flag){
        var entry=LearningDatasets.get(dataset);
        if(entry==null){lines.accept(Component.translatable("tooltip.aesi.module_obsolete"));return;}
        lines.accept(Component.translatable("tooltip.aesi.dataset_"+entry.kind(),entry.grants().size()));
        lines.accept(Component.translatable("tooltip.aesi.dataset_station",Component.translatable("dataset.aesi."+dataset+".station")));
        for(var grant:entry.grants())lines.accept(Component.literal("  "+grant.family()
                +(grant.maxEu()==Long.MAX_VALUE?"":" <= "+grant.maxEu()+" EU/t")));
        if(!entry.loaded())lines.accept(Component.translatable("tooltip.aesi.dataset_unavailable"));
        lines.accept(Component.translatable("tooltip.aesi.module_install"));
    }
}
