package com.xy2407.nsukaddition.client.foreigntrade;

import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketDataRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketModifyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** 自由市场修改上架弹窗，编辑物品数量和价格。 */
@OnlyIn(Dist.CLIENT)
public class FreeMarketModifyPopupScreen extends Screen {

    private final BlockPos boxPos;
    private final long listingId;
    private final String itemId;
    private final String itemNbt;
    private final int currentCount;
    private final int currentPrice;
    private final Screen parentScreen;
    private EditBox countInput;
    private EditBox priceInput;

    private static final int POPUP_W = 200;
    private static final int POPUP_H = 160;

    public FreeMarketModifyPopupScreen(BlockPos boxPos, long listingId, String itemId, int currentCount, int currentPrice, String itemNbt, Screen parentScreen) {
        super(Component.literal("修改上架信息"));
        this.boxPos = boxPos;
        this.listingId = listingId;
        this.itemId = itemId;
        this.currentCount = currentCount;
        this.currentPrice = currentPrice;
        this.itemNbt = itemNbt;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int baseX = leftPos() + 20;
        int baseY = topPos() + 50;

        countInput = new EditBox(Minecraft.getInstance().font, baseX + 60, baseY, 100, 18, Component.literal("数量"));
        countInput.setMaxLength(6);
        countInput.setValue(String.valueOf(currentCount));
        countInput.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(countInput);

        priceInput = new EditBox(Minecraft.getInstance().font, baseX + 60, baseY + 28, 100, 18, Component.literal("价格"));
        priceInput.setMaxLength(6);
        priceInput.setValue(String.valueOf(currentPrice));
        priceInput.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(priceInput);

        addRenderableWidget(Button.builder(Component.literal("确认"), b -> onConfirm())
                .bounds(baseX, baseY + 60, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(baseX + 80, baseY + 60, 60, 20).build());
    }

    private int leftPos() { return (width - POPUP_W) / 2; }
    private int topPos() { return (height - POPUP_H) / 2; }

    private void onConfirm() {
        int count = parseIntSafe(countInput.getValue(), currentCount);
        int price = parseIntSafe(priceInput.getValue(), currentPrice);
        if (count <= 0 || price <= 0) return;
        PacketDistributor.sendToServer(new FreeMarketModifyPacket(boxPos, listingId, count, price));
        if (boxPos != null) {
            PacketDistributor.sendToServer(new FreeMarketDataRequestPacket(boxPos));
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(parentScreen);
        else super.onClose();
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg, mouseX, mouseY, partialTick);
        int px = leftPos();
        int py = topPos();

        gg.fill(px, py, px + POPUP_W, py + POPUP_H, 0xFFE8E8E8);
        gg.renderOutline(px, py, POPUP_W, POPUP_H, 0xFF000000);

        ItemStack stack = ItemStack.EMPTY;
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                stack = ItemStack.parseOptional(
                        Minecraft.getInstance().level.registryAccess(),
                        (net.minecraft.nbt.CompoundTag) tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        if (!stack.isEmpty()) {
            gg.renderItem(stack, px + 20, py + 12);
            gg.drawString(font, stack.getHoverName().getString(), px + 42, py + 16, 0xFF1A1A1A);
        } else {
            gg.drawString(font, itemId, px + 20, py + 16, 0xFF1A1A1A);
        }

        int baseX = px + 20;
        int baseY = py + 50;
        gg.drawString(font, "数量:", baseX, baseY + 4, 0xFF1A1A1A);
        gg.drawString(font, "价格:", baseX, baseY + 32, 0xFF1A1A1A);

        super.render(gg, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (countInput != null && countInput.isFocused() && countInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (priceInput != null && priceInput.isFocused() && priceInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (countInput != null && countInput.isFocused() && countInput.charTyped(codePoint, modifiers)) return true;
        if (priceInput != null && priceInput.isFocused() && priceInput.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
