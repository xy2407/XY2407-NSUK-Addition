package com.xy2407.nsukaddition.common.capture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Set;

/** 实体 NBT 净化：剔除完全无用的运行时瞬态字段，保留个体特征字段（Age/Saddle/Attributes/装备/能力等）。 */
public final class EntityNbtSanitizer {

    private static final Set<String> USELESS_KEYS = Set.of(
            "Brain", "HurtByTimestamp", "Motion", "OnGround", "FallDistance", "FallFlying",
            "PortalCooldown", "AbsorptionAmount", "CanUpdate", "CanPickUpLoot",
            "DeathTime", "HurtTime", "Fire", "Air", "LeftHanded", "InLove",
            "ForcedAge", "PersistenceRequired", "forge:spawn_type",
            "Pos", "Rotation", "UUID", "Health"
    );

    private EntityNbtSanitizer() {}

    public static CompoundTag sanitize(CompoundTag source) {
        CompoundTag copy = source.copy();
        for (String key : USELESS_KEYS) {
            copy.remove(key);
        }
        trimEmptySlots(copy, "HandItems");
        trimEmptySlots(copy, "ArmorItems");
        return copy;
    }

    private static void trimEmptySlots(CompoundTag tag, String key) {
        if (!tag.contains(key)) return;
        ListTag list = tag.getList(key, 10);
        boolean allEmpty = true;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        if (allEmpty) {
            tag.remove(key);
        }
    }
}