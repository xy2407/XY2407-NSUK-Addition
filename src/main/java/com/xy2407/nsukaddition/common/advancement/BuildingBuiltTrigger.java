package com.xy2407.nsukaddition.common.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/** 建筑完工时触发：按建筑结构内包含的控制盒方块识别对应成就。 */
public class BuildingBuiltTrigger extends SimpleCriterionTrigger<BuildingBuiltTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Block controlBoxBlock) {
        trigger(player, instance -> instance.matches(controlBoxBlock));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation block) implements SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                builder -> builder.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        ResourceLocation.CODEC.fieldOf("block").forGetter(TriggerInstance::block)
                ).apply(builder, TriggerInstance::new)
        );

        boolean matches(Block block) {
            return this.block.equals(BuiltInRegistries.BLOCK.getKey(block));
        }

        public static Criterion<TriggerInstance> atBlock(ResourceLocation block) {
            return NsukTriggers.BUILDING_BUILT.get().createCriterion(new TriggerInstance(Optional.empty(), block));
        }
    }
}