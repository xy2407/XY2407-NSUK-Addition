package com.xy2407.nsukaddition.server.event;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 玩家首次登录进入世界时赠送开局物资（蓝图、农业方块、建筑盒、背包与枪械），仅发送一次。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class FirstLoginGiftHandler {

    private static final String GIFT_TAG = "nsukaddition_first_login_gift";

    private FirstLoginGiftHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(GIFT_TAG)) return;
        data.putBoolean(GIFT_TAG, true);

        grant(player, "xy2407_nsuk_addition:city_core_placer", null, 1);
        grant(player, "simukraft:nsuk_farmland_box", null, 1);
        grant(player, "simukraft:build_box", null, 1);
        grant(player, "simukraft:logistics_server_box", null, 1);
        grant(player, "sophisticatedbackpacks:netherite_backpack", null, 1);
        grant(player, "tacz:modern_kinetic_gun", gunTag(), 1);
        grant(player, "tacz:ammo_box", ammoTag(), 1);
    }

    private static CompoundTag gunTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("GunCurrentAmmoCount", 24);
        tag.putString("GunFireMode", "AUTO");
        tag.putString("GunId", "tacz:ump45");
        tag.putByte("HasBulletInBarrel", (byte) 1);
        return tag;
    }

    private static CompoundTag ammoTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("AmmoCount", 540);
        tag.putString("AmmoId", "tacz:45acp");
        tag.putInt("Level", 2);
        return tag;
    }

    private static void grant(ServerPlayer player, String itemId, CompoundTag customData, int count) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == Items.AIR) return;
        ItemStack stack = new ItemStack(item, count);
        if (customData != null) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}