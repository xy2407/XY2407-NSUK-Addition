package com.xy2407.nsukaddition.common.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** 城市等级升级时触发的成就条件。 */
public class CityLevelUpTrigger extends SimpleCriterionTrigger<CityLevelUpTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int newLevel) {
        trigger(player, instance -> instance.matches(newLevel));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int targetLevel) implements SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                builder -> builder.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Codec.INT.fieldOf("target_level").forGetter(TriggerInstance::targetLevel)
                ).apply(builder, TriggerInstance::new)
        );

        boolean matches(int level) {
            return level >= targetLevel;
        }

        public static Criterion<TriggerInstance> atLevel(int targetLevel) {
            return NsukTriggers.CITY_LEVEL_UP.get().createCriterion(
                    new TriggerInstance(Optional.empty(), targetLevel));
        }
    }
}
