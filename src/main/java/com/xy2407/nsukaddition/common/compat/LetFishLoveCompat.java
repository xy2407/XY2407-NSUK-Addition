package com.xy2407.nsukaddition.common.compat;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import com.chinaex123.letfishlove.entity.FishBreedingUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Let Fish Love Rewrite 模组兼容层，封装鱼类繁殖能力查询与触发。 */
public final class LetFishLoveCompat {

    private LetFishLoveCompat() {}

    private static void log(String msg) {
        try {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[LFLR兼容] " + msg), false);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("letfishlove");
    }

    private static FishBreedingCap getOrCacheCap(WaterAnimal fish) {

        FishBreedingCap cap = FishBreedingUtil.getFishCap(fish);
        if (cap != null) {

            if (!FishBreedingCapAttacher.CAPABILITY_CACHE.containsKey(fish.getUUID())) {
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
                log("getOrCacheCap: 临时cap已缓存 fish=" + fish.getName().getString());
            }
            return cap;
        }

        try {
            cap = fish.getCapability(FishBreedingCapAttacher.FISH_BREEDING_CAPABILITY);
            if (cap != null) {
                FishBreedingCapAttacher.CAPABILITY_CACHE.put(fish.getUUID(), cap);
                log("getOrCacheCap: 通过capability获取并缓存 fish=" + fish.getName().getString());
                return cap;
            }
        } catch (Exception ignored) {}
        log("getOrCacheCap: 彻底失败 cap=null fish=" + fish.getName().getString());
        return null;
    }

    public static boolean canFallInLove(WaterAnimal fish) {
        if (!isLoaded()) return true;
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap == null) {
                log("canFallInLove: cap=null -> false (fish=" + fish.getName().getString() + ")");
                return false;
            }
            boolean result = cap.canFallInLove();
            log("canFallInLove: cap=" + cap + " result=" + result);
            return result;
        } catch (Exception e) {
            log("canFallInLove: exception " + e.getClass().getSimpleName() + " -> false");
            return false;
        }
    }

    public static void setInLove(WaterAnimal fish, ServerLevel level) {
        if (!isLoaded()) { log("setInLove: LFLR未加载, 跳过"); return; }
        try {
            FishBreedingCap cap = getOrCacheCap(fish);
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLove(fish, null, level);

                FishBreedingCapAttacher.CAPABILITY_CACHE.putIfAbsent(fish.getUUID(), cap);
                log("setInLove: 成功, cap已缓存, cooldown已重置");
            } else {
                log("setInLove: cap=null, 跳过");
            }
        } catch (Exception e) {
            log("setInLove: 异常 " + e.getClass().getSimpleName());
        }
    }

    public static boolean triggerPairInLove(ServerLevel level, WaterAnimal first, WaterAnimal second) {
        log("triggerPairInLove: 开始, first=" + first + " second=" + second);
        if (!isLoaded()) { log("triggerPairInLove: LFLR未加载 -> false"); return false; }
        try {
            if (!canFallInLove(first) || !canFallInLove(second)) {
                log("triggerPairInLove: canFallInLove检查失败 -> false");
                return false;
            }
            setInLove(first, level);
            setInLove(second, level);
            log("triggerPairInLove: 成功 -> true");
            return true;
        } catch (Exception ignored) {
            log("triggerPairInLove: 异常 " + ignored.getClass().getSimpleName() + " -> false");
            return false;
        }
    }

    public static boolean canFallInLove(Mob fish) {
        if (!isLoaded()) return false;
        try {
            FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
            if (cap == null) {
                log("canFallInLove(Mob): 缓存中无cap fish=" + fish.getName().getString());
                return false;
            }
            return cap.canFallInLove();
        } catch (Exception e) {
            log("canFallInLove(Mob): exception -> false");
            return false;
        }
    }

    public static void setInLove(Mob fish, ServerLevel level) {
        if (!isLoaded()) { log("setInLove(Mob): LFLR未加载"); return; }
        try {
            FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLoveInt(600, true);
                cap.setLoveCauseUUID(null, true);
                log("setInLove(Mob): 成功 cap已设 inLove=600");
            } else {
                log("setInLove(Mob): cap=null fish=" + fish.getName().getString());
            }
        } catch (Exception e) {
            log("setInLove(Mob): exception " + e.getClass().getSimpleName());
        }
    }

    public static void setInLove(Mob fish, net.minecraft.world.entity.player.Player player, Level level) {
        if (!isLoaded()) { log("setInLove(Mob,Player): LFLR未加载"); return; }
        try {
            FishBreedingCap cap = FishBreedingCapAttacher.CAPABILITY_CACHE.get(fish.getUUID());
            if (cap != null) {
                cap.setCanLoveCooldown(0, true);
                cap.setInLoveInt(600, true);
                cap.setLoveCauseUUID(player.getUUID(), true);

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.broadcastEntityEvent(fish, (byte) 18);
                }
                log("setInLove(Mob,Player): 成功 fish=" + fish.getName().getString());
            } else {
                log("setInLove(Mob,Player): cap=null fish=" + fish.getName().getString());
            }
        } catch (Exception e) {
            log("setInLove(Mob,Player): exception " + e.getClass().getSimpleName());
        }
    }

    public static boolean triggerPairInLove(ServerLevel level, Mob first, Mob second) {
        log("triggerPairInLove(Mob): 开始, first=" + first + " second=" + second);
        if (!isLoaded()) { log("triggerPairInLove(Mob): LFLR未加载 -> false"); return false; }
        try {
            if (!canFallInLove(first) || !canFallInLove(second)) {
                log("triggerPairInLove(Mob): canFallInLove检查失败 -> false");
                return false;
            }
            setInLove(first, level);
            setInLove(second, level);
            log("triggerPairInLove(Mob): 成功 -> true");
            return true;
        } catch (Exception ignored) {
            log("triggerPairInLove(Mob): 异常 -> false");
            return false;
        }
    }
}
