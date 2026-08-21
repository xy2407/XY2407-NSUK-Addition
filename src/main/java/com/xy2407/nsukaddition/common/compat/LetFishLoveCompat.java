package com.xy2407.nsukaddition.common.compat;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Let Fish Love Rewrite 模组兼容层，封装鱼类繁殖能力查询与触发。 */
public final class LetFishLoveCompat {

    private LetFishLoveCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("letfishlove");
    }

    private static FishBreedingCap getOrCacheCap(WaterAnimal fish) {
        FishBreedingCap cap = FishBreedingUtil.getFishCap(fish);
        if (cap != null) {
            if (!FishBreedingCapAttacher.CAPABILITY_CACHE.containsKey(fish.getUUID())) {
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
            }
            return cap;
        }
        try {
            cap = fish.getCapability(FishBreedingCapAttacher.FISH_BREEDING_CAPABILITY);
            if (cap != null) {
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
                return cap;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static FishBreedingCap getOrCacheCap(Mob fish) {
        FishBreedingCap cached = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
        if (cached != null) {
            return cached;
        }
        try {
            FishBreedingCap cap = fish.getCapability(FishBreedingCapAttacher.FISH_BREEDING_CAPABILITY);
            if (cap != null) {
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
                return cap;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean canFallInLove(WaterAnimal fish) {
        if (!isLoaded()) return true;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap == null) {
                return false;
            }
            return cap.canFallInLove();
        } catch (Exception e) {
            return false;
        }
    }

    public static void setInLove(WaterAnimal fish, ServerLevel level) {
        if (!isLoaded()) return;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLove(fish, null, level);
                FishBreedingCapAttacher.CAPABILITY_CACHE.putIfAbsent(fish.getUUID(), cap);
            }
        } catch (Exception ignored) {}
    }

    public static boolean triggerPairInLove(ServerLevel level, WaterAnimal first, WaterAnimal second) {
        if (!isLoaded()) return false;
        try {
            if (!canFallInLove(first) || !canFallInLove(second)) {
                return false;
            }
            setInLove(first, level);
            setInLove(second, level);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean canFallInLove(Mob fish) {
        if (!isLoaded()) return false;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap == null) {
                return false;
            }
            return cap.canFallInLove();
        } catch (Exception e) {
            return false;
        }
    }

    public static void setInLove(Mob fish, ServerLevel level) {
        if (!isLoaded()) return;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLoveInt(600, true);
                cap.setLoveCauseUUID(null, true);
            }
        } catch (Exception ignored) {}
    }

    public static void setInLove(Mob fish, net.minecraft.world.entity.player.Player player, Level level) {
        if (!isLoaded()) return;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLoveInt(600, true);
                cap.setLoveCauseUUID(player.getUUID(), true);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.broadcastEntityEvent(fish, (byte) 18);
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean triggerPairInLove(ServerLevel level, Mob first, Mob second) {
        if (!isLoaded()) return false;
        try {
            if (!canFallInLove(first) || !canFallInLove(second)) {
                return false;
            }
            setInLove(first, level);
            setInLove(second, level);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
