package com.xy2407.nsukaddition.common.mining;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

/** 挖矿控制盒菜单的物品槽位布局根元素，包含镐槽与玩家背包。 */
@SuppressWarnings("null")
public final class MiningControlBoxMenuSlotsRoot extends UIElement {
    private static final int SLOT_SIZE = 18;

    public MiningControlBoxMenuSlotsRoot(Player player, MiningControlBoxOpenResponsePacket packet) {
        layout(l -> {
            l.width(380);
            l.height(280);
            l.flexDirection(FlexDirection.COLUMN);
            l.gapAll(4);
            l.paddingAll(10);
        });
        addChild(pickaxeSlots(player, packet));
        addChild(playerInventorySlots());
    }

    private UIElement pickaxeSlots(Player player, MiningControlBoxOpenResponsePacket packet) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(SLOT_SIZE + 4);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(2);
            l.justifyContent(AlignContent.CENTER);
            l.alignItems(AlignItems.CENTER);
        });

        BlockEntity be = player.level().getBlockEntity(packet.boxPos());
        ItemStackHandler handler = be instanceof MiningControlBoxBlockEntity box ? box.pickaxes() : null;
        for (int i = 0; i < MiningControlBoxBlockEntity.PICKAXE_SLOTS; i++) {
            ItemSlot slot = new ItemSlot();
            if (handler != null) {
                slot.bind(handler, i);
            }
            slot.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE).flexShrink(0));
            slot.slotStyle(s -> s.acceptQuickMove(true));
            row.addChild(slot);
        }
        return row;
    }

    private InventorySlots playerInventorySlots() {
        InventorySlots slots = new InventorySlots();
        slots.layout(l -> l.widthPercent(100).height(76));
        return slots;
    }
}
