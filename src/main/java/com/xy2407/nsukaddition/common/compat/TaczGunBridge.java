package com.xy2407.nsukaddition.common.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * TACZ 枪械软依赖反射桥：探测 TACZ 运行时类，反射驱动开火、出枪、枪托近战与拉栓。
 */
public final class TaczGunBridge {
    private static final String CLASS_IGUN = "com.tacz.guns.api.item.IGun";
    private static final String CLASS_IGUN_OPERATOR = "com.tacz.guns.api.entity.IGunOperator";

    private static volatile Boolean taczLoaded;
    private static volatile Method methodGetIGunOrNull;
    private static volatile Method methodFromLivingEntity;
    private static volatile Method methodShoot;
    private static volatile Method methodMelee;
    private static volatile Method methodDraw;
    private static volatile Method methodBolt;
    private static volatile Method methodReload;

    private TaczGunBridge() {
    }

    public static boolean isTaczLoaded() {
        if (taczLoaded == null) {
            taczLoaded = probe();
        }
        return taczLoaded;
    }

    private static boolean probe() {
        try {
            Class<?> igun = Class.forName(CLASS_IGUN);
            Class<?> operator = Class.forName(CLASS_IGUN_OPERATOR);
            methodGetIGunOrNull = igun.getMethod("getIGunOrNull", ItemStack.class);
            methodFromLivingEntity = operator.getMethod("fromLivingEntity", LivingEntity.class);
            methodShoot = operator.getMethod("shoot", Supplier.class, Supplier.class);
            methodMelee = operator.getMethod("melee");
            methodDraw = operator.getMethod("draw", Supplier.class);
            methodBolt = operator.getMethod("bolt");
            methodReload = operator.getMethod("reload");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static boolean isGunItem(ItemStack stack) {
        if (!isTaczLoaded() || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return methodGetIGunOrNull.invoke(null, stack) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static boolean isGunHeld(LivingEntity entity) {
        if (!isTaczLoaded() || entity == null) {
            return false;
        }
        return isGunItem(entity.getMainHandItem()) || isGunItem(entity.getOffhandItem());
    }

    public static void draw(LivingEntity entity, ItemStack gun) {
        if (!isTaczLoaded() || entity == null || gun == null || gun.isEmpty()) {
            return;
        }
        try {
            methodDraw.invoke(operator(entity), (Supplier<ItemStack>) () -> gun);
        } catch (ReflectiveOperationException e) {
        }
    }

    public static String shoot(LivingEntity entity, float pitch, float yaw) {
        if (!isTaczLoaded() || entity == null) {
            return "NOT_GUN";
        }
        try {
            Object result = methodShoot.invoke(operator(entity), (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw);
            return result == null ? "UNKNOWN_FAIL" : result.toString();
        } catch (ReflectiveOperationException e) {
            return "UNKNOWN_FAIL";
        }
    }

    public static void melee(LivingEntity entity) {
        if (!isTaczLoaded() || entity == null) {
            return;
        }
        try {
            methodMelee.invoke(operator(entity));
        } catch (ReflectiveOperationException e) {
        }
    }

    public static void bolt(LivingEntity entity) {
        if (!isTaczLoaded() || entity == null) {
            return;
        }
        try {
            methodBolt.invoke(operator(entity));
        } catch (ReflectiveOperationException e) {
        }
    }

    public static void reload(LivingEntity entity) {
        if (!isTaczLoaded() || entity == null) {
            return;
        }
        try {
            methodReload.invoke(operator(entity));
        } catch (ReflectiveOperationException e) {
        }
    }

    private static Object operator(LivingEntity entity) throws ReflectiveOperationException {
        return methodFromLivingEntity.invoke(null, entity);
    }
}