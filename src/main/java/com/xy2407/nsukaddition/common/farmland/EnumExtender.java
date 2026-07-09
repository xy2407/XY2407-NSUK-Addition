package com.xy2407.nsukaddition.common.farmland;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;

/** 通过 Unsafe 在运行时向枚举类动态注入新的常量实例。 */
public final class EnumExtender {
    private static final Unsafe UNSAFE = getUnsafe();

    private EnumExtender() {
    }

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("无法获取 sun.misc.Unsafe", e);
        }
    }

    public record FieldValue(String name, Object value) {
    }

    /** 注入新枚举常量并返回创建的实例 */
    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T addEnumConstant(
            Class<T> enumClass,
            String name,
            FieldValue... fieldValues) {
        try {

            T instance = (T) UNSAFE.allocateInstance(enumClass);

            Field nameField = Enum.class.getDeclaredField("name");
            UNSAFE.putObject(instance, UNSAFE.objectFieldOffset(nameField), name);

            Field valuesField = findValuesField(enumClass);
            long valuesOffset = UNSAFE.staticFieldOffset(valuesField);
            T[] oldValues = (T[]) UNSAFE.getObject(enumClass, valuesOffset);

            Field ordinalField = Enum.class.getDeclaredField("ordinal");
            UNSAFE.putInt(instance, UNSAFE.objectFieldOffset(ordinalField), oldValues.length);

            for (FieldValue fv : fieldValues) {
                Field f = enumClass.getDeclaredField(fv.name);
                UNSAFE.putObject(instance, UNSAFE.objectFieldOffset(f), fv.value);
            }

            T[] newValues = Arrays.copyOf(oldValues, oldValues.length + 1);
            newValues[oldValues.length] = instance;
            UNSAFE.putObject(enumClass, valuesOffset, newValues);

            clearEnumCache(enumClass);

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("注入 enum 常量失败: " + name + " -> " + enumClass.getName(), e);
        }
    }

    private static Field findValuesField(Class<?> enumClass) throws NoSuchFieldException {
        for (Field f : enumClass.getDeclaredFields()) {
            if (f.getName().equals("$VALUES") && f.getType().isArray()) {
                return f;
            }
        }
        throw new NoSuchFieldException("$VALUES not found in " + enumClass.getName());
    }

    private static void clearEnumCache(Class<?> enumClass) {
        try {
            Field f = Class.class.getDeclaredField("enumConstants");
            UNSAFE.putObject(enumClass, UNSAFE.objectFieldOffset(f), null);
        } catch (NoSuchFieldException ignored) {
        }
        try {
            Field f = Class.class.getDeclaredField("enumConstantDirectory");
            UNSAFE.putObject(enumClass, UNSAFE.objectFieldOffset(f), null);
        } catch (NoSuchFieldException ignored) {
        }
    }
}
