package com.xy2407.nsukaddition.server.citizen;

import com.mojang.brigadier.Command;
import com.xy2407.nsukaddition.common.menu.CitizenEquipmentMenuProvider;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** 市民装备相关命令，提供 /nsuk citizen equip 打开最近市民的装备菜单。 */
@SuppressWarnings("null")
public final class CitizenEquipmentCommand {

    private CitizenEquipmentCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nsuk")
                .then(Commands.literal("citizen")
                        .then(Commands.literal("equip")
                                .executes(context -> openNearestCitizenEquipment(context.getSource())))));
    }

    private static int openNearestCitizenEquipment(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.xy2407_nsuk_addition.command.player_required"));
            return 0;
        }
        AABB box = player.getBoundingBox().inflate(5.0D);
        List<CitizenEntity> citizens = player.level().getEntitiesOfClass(CitizenEntity.class, box,
                entity -> entity.isAlive() && !entity.isRemoved());
        if (citizens.isEmpty()) {
            source.sendFailure(Component.translatable("message.xy2407_nsuk_addition.command.no_citizen_nearby"));
            return 0;
        }
        citizens.sort((a, b) -> Double.compare(player.distanceToSqr(a), player.distanceToSqr(b)));
        CitizenEntity target = citizens.getFirst();
        if (CitizenEquipmentMenuProvider.open(player, target)) {
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.translatable("message.xy2407_nsuk_addition.command.open_failed"));
        return 0;
    }
}
