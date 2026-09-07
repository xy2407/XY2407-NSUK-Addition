package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.foreigntrade.VillageStockConfig;
import com.xy2407.nsukaddition.common.modpack.DialogLinks;
import com.xy2407.nsukaddition.common.network.dialog.DialogNpcOpenPacket;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.commercial.CommercialTradeMenuProvider;
import common.cn.kafei.simukraft.commercial.CommercialTradeView;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 城市对话 NPC 对话框服务：按玩家权限与城市等级组织硬编码对话，并提供复用 simukraft 商业UI的初始物资商店。 */
public final class DialogNpcDialogService {

    private static final int SHOP_INIT_STOCK = 4000;

    private DialogNpcDialogService() {}

    public static void openFor(ServerLevel level, ServerPlayer player, DialogNpcEntity entity) {
        if (player == null || level == null || entity == null || entity.getCoreOrigin() == null) {
            return;
        }
        Optional<CityData> cityOpt = CityService.findCityByCorePos(level, entity.getCoreOrigin());
        if (cityOpt.isEmpty()) {
            send(level, player, entity, "", "城市数据异常，无法提供服务。", List.of("关闭"), List.of("leave"));
            return;
        }
        CityData city = cityOpt.get();
        if (!CityService.canManageCity(city, player.getUUID())) {
            send(level, player, entity, "", "欢迎来到" + city.cityName() + "市", List.of(), List.of());
            return;
        }
        openManagerPage(level, player, entity, city);
    }

    private static void openManagerPage(ServerLevel level, ServerPlayer player, DialogNpcEntity entity, CityData city) {
        CityLevel clevel = CityLevel.fromLevel(city.cityLevel());
        String body = clevel == CityLevel.SETTLEMENT
                ? "§6【聚落起步】§f你已初建城市，当前等级：§6»  §e聚落"
                : "§6【城市等级】§f当前等级：§6»  §e" + clevel.displayName();
        // 所有等级共用相同选项；初始物资商店仅在聚落等级出现
        if (clevel == CityLevel.SETTLEMENT) {
            send(level, player, entity, "", body,
                    List.of("我该怎么做？", "初始物资商店", "状态切换", "我想要更多建筑该怎么办？", "离开"),
                    List.of("help", "shop_open", "state_menu", "more_building", "leave"));
        } else {
            send(level, player, entity, "", body,
                    List.of("我该怎么做？", "状态切换", "我想要更多建筑该怎么办？", "离开"),
                    List.of("help", "state_menu", "more_building", "leave"));
        }
    }

    public static void handleAction(ServerLevel level, ServerPlayer player, DialogNpcEntity entity, String action) {
        if (level == null || player == null || entity == null || action == null) {
            return;
        }
        switch (action) {
            case "stay" -> {
                entity.setPatrol(false);
                reopen(level, player, entity);
            }
            case "patrol" -> {
                entity.setPatrol(true);
                reopen(level, player, entity);
            }
            case "state_menu" -> send(level, player, entity, "",
                    "请选择当前状态：",
                    List.of("待在原地", "周边巡逻", "返回"),
                    List.of("stay", "patrol", "back_home"));
            case "help" -> sendHelpPage(level, player, entity);
            case "back_help" -> sendHelpPage(level, player, entity);
            case "h_money" -> send(level, player, entity, "",
                    "§6【资金获取】§f前期可通过探索宝箱获得启动资金；\n"
                            + "随着发展建立生产体系，可对其他城市出口物资换取收益；\n"
                            + "游客进入城市后会在[餐厅]消费，每日还可收取市民租金（受繁荣度影响）。\n"
                            + "§7可在[城市核心方块]财政面板，或按§b[Tab]§7打开侧边栏的「资金流水」查看明细。",
                    List.of("返回"), List.of("back_help"));
            case "h_warehouse" -> send(level, player, entity, "",
                    "§6【物流仓库】§f取出背包中的[物流服务端]并安放，\n"
                            + "右键选择[创建仓库]、划定范围后即可建立。\n"
                            + "仓库是城市物资中枢，建筑/工业/餐饮/养殖/农业等进出货统一经此流转。",
                    List.of("返回"), List.of("back_help"));
            case "h_housing" -> send(level, player, entity, "",
                    "§6【住宅与人口】§f[住宅控制箱]是入住核心，单个建筑入住上限由所含[红床]数量决定。\n"
                            + "人口越多，可供支配的劳动力越多，每日租金收益也越大。",
                    List.of("返回"), List.of("back_help"));
            case "h_farm" -> send(level, player, entity, "",
                    "§6【农业发展】§f放置[农业工作盒]，选择作物、划设地块并雇佣农夫。\n"
                            + "开始工作后员工将自行作业，务必在容器内放入种子与基础材料。\n"
                            + "§7注意按季节与湿度选择合适的作物，否则无法正常生长。",
                    List.of("返回"), List.of("back_help"));
            case "h_advice" -> send(level, player, entity, "",
                    "§6【发展建议】§f推荐次序：仓库→建筑盒→住宅→农业→养殖→工业。\n"
                            + "先建仓库保证物资运输通畅，再建住宅扩大人口；\n"
                            + "农业补充粮食后，再逐步引入养殖、餐饮与工业。\n"
                            + "§7后续可与其它城市建立外贸关系，对外出口进一步扩展收益渠道。",
                    List.of("返回"), List.of("back_help"));
            case "h_upgrade" -> send(level, player, entity, "", buildUpgradeBody(),
                    List.of("返回"), List.of("back_help"));
            case "more_building" -> send(level, player, entity, "", buildMoreBuildingBody(),
                    List.of(
                            "前往mcblock建筑素材库",
                            "前往createmod机械动力素材库",
                            "我该如何导入建筑呢？（视频）",
                            "投稿原创建筑作品 / 加入整合包交流群",
                            "返回"),
                    List.of(
                            "url:mcblock",
                            "url:createmod",
                            "url:import_building",
                            "url:qq_group",
                            "back_home"));
            case "video_home" -> send(level, player, entity, "",
                    "欢迎在此处直接查看视频教程\n我图省事直接传b站了，点击对应选项即可跳转",
                    List.of("城市核心方块介绍", "物流仓库搭建", "建筑盒使用", "农业盒/农田系统", "探矿与开采",
                            "养殖系统", "餐厅系统", "工业系统", "附属地/殖民地", "外贸/商队", "RTS模式", "返回"),
                    List.of("url:city_core", "url:warehouse", "url:build_box", "url:farm_land", "url:prospecting",
                            "url:breeding", "url:restaurant", "url:industrial", "url:colony", "url:foreign_trade",
                            "url:rts", "back_help"));
            case "back_home" -> reopen(level, player, entity);
            case "leave" -> {
                // 关闭对话框不改变玩家选择的待在原地/巡逻状态，保持原选择，避免覆盖。
            }
            case "shop_open" -> {
                player.closeContainer();
                CommercialTradeMenuProvider.open(player, buildInitialShopView(level, entity.getUUID()));
            }
            default -> {
            }
        }
    }

    private static void sendHelpPage(ServerLevel level, ServerPlayer player, DialogNpcEntity entity) {
        send(level, player, entity, "",
                "§6【发展指引】§f初建城市后，建议按以下次序推进：\n"
                        + "§e① 物流仓库 §f——取出背包中的[物流服务端]，右键创建仓库，施工材料统一经此流转\n"
                        + "§e② 建筑盒 §f——雇佣建筑师建造住宅与生产建筑\n"
                        + "§e③ 生产体系 §f——逐步完善农业、餐饮与工业，稳步发展聚落\n"
                        + "§7各环节详解可点击下方对应选项查看。",
                List.of("如何获取资金", "如何搭建城市仓库", "什么是住宅/人口", "如何发展农业", "城市升级", "视频教程", "返回"),
                List.of("h_money", "h_warehouse", "h_housing", "h_farm", "h_upgrade", "video_home", "back_home"));
    }

    /** 更多建筑的说明正文。 */
    private static String buildMoreBuildingBody() {
        return "很抱歉，整合包处于刚刚起步阶段，没钱也没时间对建筑内容进行大幅扩展\n"
                + "但是，你如果想要增加新的建筑，欢迎自行去各大建筑网站获取更多的建筑来丰富你的城市\n"
                + "诸如mcblock的建筑库中有着大量的素材，搭配simcity expansion即可导入为建筑盒可选建筑，加入到你的城市中\n"
                + "或是createmod.com中有着大量机械动力和航空学的建筑素材，也可通过simcity expansion导入\n"
                + "欢迎自行探索\n"
                + "simcity expansion的界面默认按键为[\\]";
    }

    /** 由城市等级概览拼装升级说明，仅展示等级与领地上限，具体升级条件查阅城市核心方块避免重复冗余。 */
    private static String buildUpgradeBody() {
        StringBuilder sb = new StringBuilder();
        CityLevel[] levels = CityLevel.values();
        sb.append("§6【城市等级】§f当前共§e").append(levels.length).append("§f级：\n");
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) {
                sb.append("§f》");
            }
            sb.append("§e").append(levels[i].displayName());
        }
        sb.append("\n\n§6【领地上限】§f（区块）：");
        for (CityLevel cl : levels) {
            sb.append("\n§e").append(cl.displayName()).append("§f：§b").append(cl.maxChunks()).append(" 区块");
        }
        sb.append("\n\n§7解锁与升级详情请见【城市核心方块】内的【城市升级】索引。");
        sb.append("\n§7城市达到城镇等级后，可开辟附属领地进行自定义发展。");
        return sb.toString();
    }

    private static void reopen(ServerLevel level, ServerPlayer player, DialogNpcEntity entity) {
        openFor(level, player, entity);
    }

    /** 由 refresh 请求按世界管家实体重建初始商店视图。workerId 为世界管家的 UUID。 */
    public static CommercialTradeView buildInitialShopView(ServerLevel level, UUID workerId) {
        if (level == null || workerId == null) {
            return null;
        }
        if (!(level.getEntity(workerId) instanceof DialogNpcEntity entity) || entity.getCoreOrigin() == null) {
            return null;
        }
        Optional<CityData> cityOpt = CityService.findCityByCorePos(level, entity.getCoreOrigin());
        double balance = cityOpt.map(CityData::funds).orElse(0.0D);
        List<CommercialTradeView.OfferEntry> offers = new ArrayList<>();
        for (ForeignTradeConfig.TradeItemDef def : ForeignTradeConfig.getEntries()) {
            if (def == null || def.isAnimal() || !VillageStockConfig.isMaterialCategory(def.category())) {
                continue;
            }
            String key = def.tradeKey();
            if (!entity.hasShopStock(key)) {
                entity.setShopStock(key, SHOP_INIT_STOCK);
            }
            int stock = entity.getShopStock(key);
            double price = shopPrice(key);
            // 仅购买：玩家付钱(money)得到建材(item)，不提供向NPC出售的方向。
            offers.add(new CommercialTradeView.OfferEntry(
                    "dshop_" + key,
                    List.of(new CommercialTradeView.ResourceEntry("money", "", 0, price)),
                    List.of(new CommercialTradeView.ResourceEntry("item", key, 1, 0)),
                    key,
                    stock,
                    SHOP_INIT_STOCK,
                    0L,
                    0));
        }
        return new CommercialTradeView(
                entity.blockPosition().immutable(),
                workerId,
                "初始物资商店",
                "城市管家",
                balance,
                true,
                offers
        );
    }

    /** 执行初始物资商店的购买：从城市金库扣款、扣减实体库存并发放建材，随后回推刷新视图。 */
    public static boolean executeInitialShopBuy(ServerLevel level, ServerPlayer player, DialogNpcEntity entity,
                                                String itemId, int count) {
        if (level == null || player == null || entity == null || itemId == null || count <= 0) {
            return false;
        }
        ForeignTradeConfig.TradeItemDef def = ForeignTradeConfig.find(itemId);
        if (def == null) {
            return false;
        }
        int stock = entity.getShopStock(itemId);
        if (count > stock) {
            count = stock;
        }
        if (count <= 0) {
            return false;
        }
        double unit = shopPrice(itemId);
        double total = unit * count;
        Optional<CityData> cityOpt = CityService.findCityByCorePos(level, entity.getCoreOrigin());
        if (cityOpt.isEmpty()) {
            return false;
        }
        UUID cityId = cityOpt.get().cityId();
        if (!EconomyService.canAfford(level, cityId, total)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("城市资金不足，无法购买。"), true);
            return false;
        }
        if (!EconomyService.withdrawCityFunds(level, cityId, player, total, "initial_shop")) {
            return false;
        }
        entity.setShopStock(itemId, stock - count);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item != null && item != Items.AIR) {
            int remaining = count;
            int maxStack = new ItemStack(item).getMaxStackSize();
            while (remaining > 0) {
                int batch = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(item, batch);
                if (!player.addItem(stack)) {
                    player.drop(stack, false);
                }
                remaining -= batch;
            }
        }
        CommercialTradeView refreshed = buildInitialShopView(level, entity.getUUID());
        if (refreshed != null) {
            PacketDistributor.sendToPlayer(player, CommercialTradeOpenResponsePacket.from(refreshed));
        }
        return true;
    }

    private static double shopPrice(String itemId) {
        ForeignTradeMarket.MarketEntry price = ForeignTradeMarket.getEntry(itemId);
        return price != null ? Math.round(price.sellPrice() / 3.0D * 100.0D) / 100.0D : 1.0D;
    }

    private static void send(ServerLevel level, ServerPlayer player, DialogNpcEntity entity,
                             String title, String body, List<String> options, List<String> actions) {
        if (player == null || entity == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DialogNpcOpenPacket(title, body, options, resolveActions(actions), entity.getId()));
    }

    /** 将形如 url:<key> 的动作按 DialogLinks 配置解析为 url:<实际地址>，未配置则置空，避免服务端硬编码链接。 */
    private static List<String> resolveActions(List<String> actions) {
        if (actions == null) {
            return null;
        }
        List<String> resolved = new ArrayList<>(actions.size());
        for (String action : actions) {
            if (action != null && action.startsWith("url:")) {
                String url = DialogLinks.get(action.substring(4));
                resolved.add(url != null && !url.isBlank() ? "url:" + url : "");
            } else {
                resolved.add(action);
            }
        }
        return resolved;
    }
}