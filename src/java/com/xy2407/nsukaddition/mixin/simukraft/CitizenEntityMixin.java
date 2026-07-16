package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 给 CitizenEntity 显式持久化盔甲槽位 NBT，作为原版 ArmorItems 的冗余备份。 */
@SuppressWarnings("null")
@Mixin(CitizenEntity.class)
public abstract class CitizenEntityMixin {

    @Unique
    private static final String NSUK_EQUIPMENT_TAG = "NsukEquipment";

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void nsuk$saveEquipment(CompoundTag compound, CallbackInfo ci) {
        CitizenEntity entity = (CitizenEntity) (Object) this;
        ListTag armorList = new ListTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            slotTag.putString("Slot", slot.getName());
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                slotTag.put("Item", stack.save(entity.registryAccess()));
            }
            armorList.add(slotTag);
        }
        if (!armorList.isEmpty()) {
            compound.put(NSUK_EQUIPMENT_TAG, armorList);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), remap = false)
    private void nsuk$loadEquipment(CompoundTag compound, CallbackInfo ci) {
        if (!compound.contains(NSUK_EQUIPMENT_TAG, ListTag.TAG_LIST)) {
            return;
        }
        CitizenEntity entity = (CitizenEntity) (Object) this;
        ListTag armorList = compound.getList(NSUK_EQUIPMENT_TAG, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < armorList.size(); i++) {
            CompoundTag slotTag = armorList.getCompound(i);
            EquipmentSlot slot = EquipmentSlot.byName(slotTag.getString("Slot"));
            if (slot == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR || !slotTag.contains("Item", CompoundTag.TAG_COMPOUND)) {
                continue;
            }
            ItemStack stack = ItemStack.parse(entity.registryAccess(), slotTag.getCompound("Item")).orElse(ItemStack.EMPTY);
            entity.setItemSlot(slot, stack);
        }
    }
}
