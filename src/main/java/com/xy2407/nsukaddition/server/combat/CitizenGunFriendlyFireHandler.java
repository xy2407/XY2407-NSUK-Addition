package com.xy2407.nsukaddition.server.combat;

import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * 同城 NPC 子弹友军伤害免疫：同城市的 NPC 射出的子弹不会伤害同城市的 NPC。
 * 通过 NeoForge 内置 LivingIncomingDamageEvent 拦截，从 DamageSource 解析射手（TACZ 子弹实体继承 Projectile，
 * 其 getOwner() 即射手），无需直接依赖 TACZ 事件类（TACZ 为软依赖，本类编译期不引用其任何类型）。
 */
public final class CitizenGunFriendlyFireHandler {

    private CitizenGunFriendlyFireHandler() {
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity victim)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;

        LivingEntity shooter = resolveShooter(event.getSource());
        if (!(shooter instanceof CitizenEntity npcShooter)) return;

        UUID victimCity = RtsCityAccessValidator.findNpcCityId(level, victim);
        UUID shooterCity = RtsCityAccessValidator.findNpcCityId(level, npcShooter);
        if (victimCity != null && Objects.equals(victimCity, shooterCity)) {
            event.setCanceled(true);
        }
    }

    private static LivingEntity resolveShooter(DamageSource source) {
        Entity indirect = source.getEntity();
        if (indirect instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile proj && proj.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        if (direct instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}
