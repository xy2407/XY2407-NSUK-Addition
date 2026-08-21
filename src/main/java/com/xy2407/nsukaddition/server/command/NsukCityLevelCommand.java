package com.xy2407.nsukaddition.server.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 设置玩家自己城市等级的指令 /nsukcitylevel <1-5>，唯一条件是玩家拥有自己的城市。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class NsukCityLevelCommand {

    private NsukCityLevelCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nsukcitylevel")
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> setLevel(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private static int setLevel(CommandSourceStack source, int levelArg) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("该指令仅限玩家执行"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        var cityOptional = CityService.findPlayerCity(level, player.getUUID());
        if (cityOptional.isEmpty()) {
            source.sendFailure(Component.literal("你没有自己的城市，无法设置城市等级"));
            return 0;
        }
        CityData city = cityOptional.get();
        if (!CityDataService.setCityLevel(level, city.cityId(), levelArg)) {
            source.sendFailure(Component.literal("设置城市等级失败"));
            return 0;
        }
        CityLevel cityLevel = CityLevel.fromLevel(levelArg);
        source.sendSuccess(() -> Component.literal("已将城市「" + city.cityName() + "」等级设置为 "
                + cityLevel.displayName() + "（领地上限 " + cityLevel.maxChunks() + " chunk）"), false);
        return 1;
    }
}