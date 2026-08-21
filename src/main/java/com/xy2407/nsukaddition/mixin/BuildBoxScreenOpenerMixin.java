package com.xy2407.nsukaddition.mixin;

import client.cn.kafei.simukraft.client.buildbox.BuildingListScreenOpener;
import com.xy2407.nsukaddition.NsukAddition;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修改 BuildBoxScreenOpener，在建筑选择界面注入养殖和外贸分类按钮及分类处理。 */
@Mixin(targets = "client.cn.kafei.simukraft.client.buildbox.BuildBoxScreenOpener", remap = false)
public class BuildBoxScreenOpenerMixin {

    private static final ThreadLocal<BlockPos> POS_HOLDER = new ThreadLocal<>();

    @Inject(method = "createSelectBuildingUi",
            at = @At("HEAD"))
    private static void nsuk$capturePos(BlockPos buildBoxPos, CallbackInfoReturnable<ModularUI> cir) {
        POS_HOLDER.set(buildBoxPos);
    }

    @ModifyVariable(method = "createSelectBuildingUi",
            at = @At("RETURN"),
            ordinal = 2)
    private static UIElement nsuk$addCategoryButtons(UIElement gridRegion) {
        if (gridRegion == null) {
            return null;
        }
        BlockPos pos = POS_HOLDER.get();
        POS_HOLDER.remove();
        if (pos == null) {
            return gridRegion;
        }

        Button breedingBtn = new Button();
        breedingBtn.setText(Component.translatable("gui.category.breeding"));
        breedingBtn.setOnClick(event -> BuildingListScreenOpener.open("breeding", pos));
        breedingBtn.layout(layout -> {
            layout.width(110);
            layout.height(20);
            layout.flexShrink(0);
        });
        gridRegion.addChild(breedingBtn);

        Button foreignTradeBtn = new Button();
        foreignTradeBtn.setText(Component.translatable("gui.category.foreign_trade"));
        foreignTradeBtn.setOnClick(event -> BuildingListScreenOpener.open("foreign_trade", pos));
        foreignTradeBtn.layout(layout -> {
            layout.width(110);
            layout.height(20);
            layout.flexShrink(0);
        });
        gridRegion.addChild(foreignTradeBtn);

        return gridRegion;
    }

    @Inject(method = "handleBuildingCategory",
            at = @At("HEAD"),
            cancellable = true)
    private static void nsuk$handleCategoryButtons(BlockPos buildBoxPos, String translationKey,
                                                   CallbackInfo ci) {
        if ("gui.category.breeding".equals(translationKey)) {
            BuildingListScreenOpener.open("breeding", buildBoxPos);
            ci.cancel();
        } else if ("gui.category.foreign_trade".equals(translationKey)) {
            BuildingListScreenOpener.open("foreign_trade", buildBoxPos);
            ci.cancel();
        }
    }
}
