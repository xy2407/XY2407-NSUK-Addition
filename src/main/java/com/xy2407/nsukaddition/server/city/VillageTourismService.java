package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityProsperityCache;
import com.xy2407.nsukaddition.common.city.TourismConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.cooking.RestaurantDiningService;
import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.commercial.*;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.xy2407.nsukaddition.common.foreigntrade.CaravanProductConfig;
import com.xy2407.nsukaddition.common.foreigntrade.CaravanProductConfig.CaravanProduct;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeMarket;
import com.xy2407.nsukaddition.common.foreigntrade.TradeItemResolver;
import com.xy2407.nsukaddition.common.foreigntrade.VillageStockService;
import com.xy2407.nsukaddition.common.storage.DailyMarkerStorage;
import com.xy2407.nsukaddition.common.storage.NsukSqliteDatabase;
import com.xy2407.nsukaddition.common.storage.WriteBatchBuffer;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 村庄旅游服务，管理游客的生成、漫游、购物消费及日落清除。 */
public final class VillageTourismService {

    private VillageTourismService() {}

    private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Tourist>> ACTIVE_TOURISTS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Long> LAST_SPAWN_TICK = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, List<Caravan>> ACTIVE_CARAVANS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_CARAVAN_SPAWN_DAY_BY_CITY = new ConcurrentHashMap<>();
    private static long lastRestockDay = -1;

    private static final ConcurrentHashMap<UUID, Double> TOURIST_INCOME = new ConcurrentHashMap<>();

    private static final long TOURIST_LIFETIME_TICKS = 10000L;
    private static final long WANDER_INTERVAL = 500L;
    private static final long DINING_INTERVAL = 1000L;
    private static final int WANDER_MAX_DIST = 5;
    private static final double DINING_EXECUTE_CHANCE = 0.60D;
    private static final int DINING_MAX_ATTEMPTS = 2;
    private static final double TIP_MAX_RATIO = 0.50D;

    public static double getTouristIncome(UUID cityId) {
        return TOURIST_INCOME.getOrDefault(cityId, 0.0D);
    }

    public static void saveTouristIncome(MinecraftServer server) {
        CompoundTag root = new CompoundTag();
        CompoundTag incomes = new CompoundTag();
        for (Map.Entry<UUID, Double> entry : TOURIST_INCOME.entrySet()) {
            if (entry.getValue() > 0.0D) {
                incomes.putDouble(entry.getKey().toString(), entry.getValue());
            }
        }
        root.put("incomes", incomes);
        try {
            Path file = incomeFilePath(server);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to save tourist income", e);
        }
    }

    public static void loadTouristIncome(MinecraftServer server) {
        Path file = incomeFilePath(server);
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            CompoundTag incomes = root.getCompound("incomes");
            for (String key : incomes.getAllKeys()) {
                try {
                    TOURIST_INCOME.put(UUID.fromString(key), incomes.getDouble(key));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load tourist income", e);
        }
    }

    private static Path incomeFilePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("nsuk_tourist_income.nbt");
    }

    public static void onServerTick(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long timeOfDay = level.getDayTime() % 24000L;
        long currentDay = level.getDayTime() / 24000L;
        long gameTime = level.getGameTime();
        if (lastRestockDay != currentDay) {
            lastRestockDay = currentDay;
            VillageStockService.tickDailyRestock(level);
        }

        boolean touristWindow = timeOfDay >= 1000L && timeOfDay < 13000L;

        if (!touristWindow) {
            if (gameTime % 2000L == 0L) {
                clearAllTourists(level);
            }
            return;
        }

        if (gameTime % 1000L == 0L) {
            for (CityData city : getPlayerCities(level)) {
                spawnTouristsForCity(level, city, timeOfDay, gameTime);
            }
        }

        if (timeOfDay >= 2000L && timeOfDay < 3000L) {
            java.util.Set<UUID> playerCityIds = new java.util.HashSet<>();
            for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
                CityService.findManagedPlayerCity(level, sp.getUUID()).ifPresent(c -> playerCityIds.add(c.cityId()));
            }
            if (playerCityIds.isEmpty()) {
                return;
            }
            for (UUID pcId : playerCityIds) {
                CityData playerCity = CityService.findCity(level, pcId).orElse(null);
                if (playerCity == null) {
                    continue;
                }
                long last = LAST_CARAVAN_SPAWN_DAY_BY_CITY.getOrDefault(pcId, -1L);
                if (last == currentDay) {
                    continue;
                }
                CityLevel cityLevel = CityLevel.fromLevel(playerCity.cityLevel());
                boolean trigger;
                if (cityLevel.atLeast(CityLevel.TOWN)) {
                    trigger = level.random.nextDouble() < 0.30D;
                } else {
                    trigger = currentDay % 2 == 0;
                }
                if (trigger) {
                    LAST_CARAVAN_SPAWN_DAY_BY_CITY.put(pcId, currentDay);
                    DailyMarkerStorage.save(level, pcId, "caravan_spawn", currentDay);
                    spawnCaravanForCity(level, playerCity, playerCity);
                }
            }
        }

        tickTouristArrivals(level);

        tickCaravans(level);
    }

    public static void beginDay(ServerLevel level, UUID cityId, long day) {
        clearAllTourists(level);

        Double accumulated = TOURIST_INCOME.remove(cityId);
        if (accumulated != null && accumulated > 0.0D) {
            EconomyService.depositCityFunds(level, cityId, null, accumulated, "tourist_caravan_income", false);
        }
    }

    public static void onCityDeleted(UUID cityId) {
        ACTIVE_TOURISTS.remove(cityId);
        LAST_SPAWN_TICK.remove(cityId);
        ACTIVE_CARAVANS.remove(cityId);
        LAST_CARAVAN_SPAWN_DAY_BY_CITY.remove(cityId);
        TOURIST_INCOME.remove(cityId);
    }

    public static boolean openCaravanTrade(ServerLevel level, ServerPlayer player, CitizenEntity leader) {
        if (level == null || player == null || leader == null) return false;
        Caravan caravan = findCaravanByLeader(leader.getUUID());
        if (caravan == null) return false;
        List<CommercialTradeView.OfferEntry> offers = new ArrayList<>();
        for (CaravanProduct p : caravan.products) {
            ForeignTradeMarket.MarketEntry price = ForeignTradeMarket.getEntry(p.itemId());
            double sellPrice = price != null ? price.sellPrice() : 1.0;
            int stock = caravan.productStock.getOrDefault(p.itemId(), 0);
            offers.add(new CommercialTradeView.OfferEntry(
                    "caravan_" + p.itemId(),
                    List.of(new CommercialTradeView.ResourceEntry("money", "", 0, sellPrice)),
                    List.of(new CommercialTradeView.ResourceEntry("item", p.itemId(), 1, 0)),
                    p.itemId(),
                    stock,
                    p.limit(),
                    0L,
                    0));
            offers.add(new CommercialTradeView.OfferEntry(
                    "caravan_sell_" + p.itemId(),
                    List.of(new CommercialTradeView.ResourceEntry("item", p.itemId(), 1, 0)),
                    List.of(new CommercialTradeView.ResourceEntry("money", "", 0, sellPrice)),
                    p.itemId(),
                    stock,
                    p.limit(),
                    0L,
                    0));
        }
        CommercialTradeView view = new CommercialTradeView(
                leader.blockPosition().immutable(),
                leader.getUUID(),
                "",
                leader.getDisplayName().getString(),
                caravan.funds,
                true,
                offers
        );
        return CommercialTradeMenuProvider.open(player, view);
    }

    public static boolean executeCaravanTrade(ServerLevel level, ServerPlayer player, UUID leaderId,
                                              String offerId, int count) {
        if (level == null || player == null || leaderId == null || offerId == null || count <= 0) {
            return false;
        }
        Caravan caravan = findCaravanByLeader(leaderId);
        if (caravan == null || !offerId.startsWith("caravan_")) {
            return false;
        }
        if (offerId.startsWith("caravan_sell_")) {
            return executeCaravanSell(level, player, caravan, offerId.substring("caravan_sell_".length()), count);
        }
        String itemId = offerId.substring("caravan_".length());
        CaravanProduct product = caravan.products.stream()
                .filter(p -> p.itemId().equals(itemId))
                .findFirst().orElse(null);
        if (product == null) {
            return false;
        }
        int stock = caravan.productStock.getOrDefault(itemId, 0);
        if (count > stock) {
            return false;
        }
        var cityOpt = CityManager.get(level).getPlayerCity(player.getUUID());
        if (cityOpt.isEmpty()) {
            return false;
        }
        UUID cityId = cityOpt.get().cityId();
        ForeignTradeMarket.MarketEntry price = ForeignTradeMarket.getEntry(itemId);
        double sellPrice = price != null ? price.sellPrice() : 1.0;
        double moneyCost = sellPrice * count;
        if (!EconomyService.canAfford(level, cityId, moneyCost)) {
            return false;
        }
        if (!EconomyService.withdrawCityFunds(level, cityId, player, moneyCost, "caravan_trade")) {
            return false;
        }
        caravan.funds += moneyCost;
        caravan.productStock.put(itemId, stock - count);
        ForeignTradeConfig.TradeItemDef def = ForeignTradeConfig.find(itemId);
        if (def == null) {
            return false;
        }
        if (def.isAnimal()) {
            ItemStack stack = TradeItemResolver.deliver(def, count);
            if (stack.isEmpty()) {
                return false;
            }
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            return true;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR) {
            return false;
        }
        int maxStack = new ItemStack(item).getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, batch);
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }
        return true;
    }

    private static boolean executeCaravanSell(ServerLevel level, ServerPlayer player, Caravan caravan,
                                              String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return false;
        }
        CaravanProduct product = caravan.products.stream()
                .filter(p -> p.itemId().equals(itemId))
                .findFirst().orElse(null);
        if (product == null) {
            return false;
        }
        int stock = caravan.productStock.getOrDefault(itemId, 0);
        if (stock + count > product.limit()) {
            return false;
        }
        var cityOpt = CityManager.get(level).getPlayerCity(player.getUUID());
        if (cityOpt.isEmpty()) {
            return false;
        }
        UUID cityId = cityOpt.get().cityId();
        ForeignTradeMarket.MarketEntry price = ForeignTradeMarket.getEntry(itemId);
        double money = (price != null ? price.sellPrice() : 1.0) * count;
        if (caravan.funds < money) {
            return false;
        }
        if (!removeFromPlayerInventory(player, itemId, count)) {
            return false;
        }
        caravan.funds -= money;
        caravan.productStock.put(itemId, stock + count);
        EconomyService.depositCityFunds(level, cityId, player, money, "caravan_sell");
        return true;
    }

    private static boolean removeFromPlayerInventory(ServerPlayer player, String itemId, int count) {
        ForeignTradeConfig.TradeItemDef def = ForeignTradeConfig.find(itemId);
        if (def == null || count <= 0) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            total += TradeItemResolver.countIn(inventory.getItem(i), def);
        }
        if (total < count) {
            return false;
        }
        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            remaining -= TradeItemResolver.removeFrom(inventory.getItem(i), def, remaining);
        }
        return remaining == 0;
    }

    @Nullable
    private static Caravan findCaravanByLeader(UUID leaderId) {
        if (leaderId == null) return null;
        for (List<Caravan> caravans : ACTIVE_CARAVANS.values()) {
            if (caravans == null) continue;
            for (Caravan caravan : caravans) {
                if (leaderId.equals(caravan.leaderId)) return caravan;
            }
        }
        return null;
    }

    public static boolean isCaravanLeader(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) return false;
        Entity entity = level.getEntity(citizenId);
        return entity != null && entity.getTags().contains(TourismConstants.TRADE_TAG);
    }

    private static List<CityData> getPlayerCities(ServerLevel level) {
        List<CityData> result = new ArrayList<>();
        if (level == null || level.getServer() == null) {
            return result;
        }
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            CityService.findManagedPlayerCity(level, sp.getUUID()).ifPresent(c -> {
                if (seen.add(c.cityId())) {
                    result.add(c);
                }
            });
        }
        return result;
    }

    private static void spawnTouristsForCity(ServerLevel level, CityData city, long timeOfDay, long gameTime) {
        UUID cityId = city.cityId();
        CopyOnWriteArrayList<Tourist> tourists = ACTIVE_TOURISTS.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>());

        int maxTourists = maxTouristsFor(level, city);
        if (tourists.size() >= maxTourists) return;

        Long lastSpawn = LAST_SPAWN_TICK.get(cityId);
        if (lastSpawn != null && gameTime - lastSpawn < 1000L) return;

        BlockPos core = city.cityCorePos();
        if (core == null) return;

        BlockPos spawnPos = findSpawnPos(level, core, cityId);
        if (spawnPos == null) return;

        CitizenEntity entity = spawnTouristEntity(level, spawnPos);
        if (entity == null) return;

        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;

        data.setCityId(null);
        data.setJobType(CityJobType.UNEMPLOYED);
        data.setHomeId(null);
        data.setWorkplaceId(null);
        data.setWorkplacePos(null);
        data.setStatusLabel(TourismConstants.TOURIST_STATUS_LABEL);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
        entity.setHunger(4.0F);

        double funds = switch (CityLevel.fromLevel(city.cityLevel())) {
            case VILLAGE -> 10.0 + level.random.nextDouble() * 90.0;
            case TOWN -> 15.0 + level.random.nextDouble() * 135.0;
            case CITY_STATE -> 20.0 + level.random.nextDouble() * 180.0;
            case METROPOLIS -> 25.0 + level.random.nextDouble() * 225.0;
            default -> 10.0 + level.random.nextDouble() * 90.0;
        };
        tourists.add(new Tourist(entity.getUUID(), funds, gameTime));
        LAST_SPAWN_TICK.put(cityId, gameTime);
    }

    private static CitizenEntity spawnTouristEntity(ServerLevel level, BlockPos pos) {
        CitizenEntity entity = ModEntities.CITIZEN.get().create(level);
        if (entity == null) {
            return null;
        }
        Vec3 target = Vec3.atBottomCenterOf(pos).add(0.0D, 1.0D, 0.0D);
        entity.moveTo(target.x, target.y, target.z, level.random.nextFloat() * 360.0F, 0.0F);
        entity.setStatusLabel(TourismConstants.TOURIST_STATUS_LABEL);
        entity.addTag(TourismConstants.TOURIST_TAG);
        level.addFreshEntity(entity);
        return entity;
    }

    private static int maxTouristsFor(ServerLevel level, CityData city) {
        CityLevel cityLevel = CityLevel.fromLevel(city.cityLevel());
        int base = switch (cityLevel) {
            case VILLAGE -> 5;
            case TOWN -> 10;
            case CITY_STATE -> 15;
            case METROPOLIS -> 20;
            default -> 0;
        };
        int cap = switch (cityLevel) {
            case VILLAGE -> 10;
            case TOWN -> 20;
            case CITY_STATE -> 30;
            case METROPOLIS -> 40;
            default -> 0;
        };
        UUID mayor = findCityMayor(city);
        int villages = countDiplomacyVillages(level, mayor);
        return Math.min(cap, base + villages * 4);
    }

    @Nullable
    private static UUID findCityMayor(CityData city) {
        if (city == null) return null;
        for (CityMemberData member : city.members()) {
            if (member.permissionLevel() == CityPermissionLevel.MAYOR) {
                return member.playerId();
            }
        }
        return null;
    }

    private static int countDiplomacyVillages(ServerLevel level, @Nullable UUID playerUuid) {
        if (level == null || playerUuid == null) return 0;
        return DiplomacyStorage.loadRelations(level, playerUuid).size();
    }

    private static boolean isInCityTerritory(ServerLevel level, UUID cityId, BlockPos pos) {
        if (level == null || cityId == null || pos == null) return false;
        long chunkLong = new ChunkPos(pos).toLong();
        return cityId.equals(CityChunkManager.get(level).getChunkOwner(chunkLong));
    }

    private static void tryRestaurantVisit(ServerLevel level, CityData city, Tourist tourist, long gameTime) {
        tourist.lastDiningTick = gameTime;
        if (level.random.nextDouble() >= DINING_EXECUTE_CHANCE) return;
        if (tourist.restaurantVisits >= 2) return;
        if (RestaurantDiningService.isDining(tourist.citizenId)) return;

        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity == null) return;

        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        double multiplier = getCityLevelMultiplier(city);
        RandomSource random = level.random;

        List<RestaurantBoxData> candidates = new ArrayList<>();
        for (RestaurantBoxData data : manager.all()) {
            if (!data.running() || data.selectedCookItems().isEmpty()) continue;
            if (!entity.blockPosition().closerThan(data.boxPos(), 256.0D)) continue;
            candidates.add(data);
        }
        if (candidates.isEmpty()) return;

        List<RestaurantBoxData> pool = new ArrayList<>(candidates);
        for (int attempt = 0; attempt < DINING_MAX_ATTEMPTS && !pool.isEmpty(); attempt++) {
            RestaurantBoxData data = pool.remove(random.nextInt(pool.size()));
            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
            if (building == null) continue;
            RestaurantDefinitionLoader.LoadResult lr = RestaurantDefinitionLoader.loadForBuilding(building);
            if (!lr.valid() || lr.definition() == null) continue;

            List<String> affordable = new ArrayList<>();
            for (String itemId : data.selectedCookItems()) {
                if (tourist.funds >= lr.definition().cookPrice(itemId) * multiplier) {
                    affordable.add(itemId);
                }
            }
            if (affordable.isEmpty()) continue;

            String itemId = affordable.get(random.nextInt(affordable.size()));
            var opt = CitizenService.findCitizen(level, tourist.citizenId);
            if (opt.isEmpty()) continue;
            if (RestaurantDiningService.startDining(level, opt.get(), building, lr.definition(), data)) {
                tourist.restaurantVisits++;
                tourist.lastRestaurantTick = gameTime;
                return;
            }
        }
    }

    private static void wanderTourist(ServerLevel level, UUID cityId, Tourist tourist) {
        tourist.lastWanderTick = level.getGameTime();
        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity == null) return;

        BlockPos pos = entity.blockPosition();
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = pos.getX() + random.nextInt(WANDER_MAX_DIST * 2 + 1) - WANDER_MAX_DIST;
            int dz = pos.getZ() + random.nextInt(WANDER_MAX_DIST * 2 + 1) - WANDER_MAX_DIST;
            if (dx == pos.getX() && dz == pos.getZ()) continue;
            BlockPos candidate = pickGroundPos(level, dx, dz);
            if (candidate == null) continue;
            if (!isInCityTerritory(level, cityId, candidate)) continue;
            CitizenNavigationService.requestMove(level, tourist.citizenId,
                    Vec3.atBottomCenterOf(candidate), MovementIntent.WALK);
            return;
        }
    }

    public static boolean canTouristAffordCheapestMeal(ServerLevel level, UUID citizenId) {
        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            for (Tourist t : entry.getValue()) {
                if (!t.citizenId.equals(citizenId)) continue;
                CitizenEntity entity = findLoadedEntity(level, citizenId);
                if (entity == null) return false;
                RestaurantBoxManager manager = RestaurantBoxManager.get(level);
                CityData city = CityDataService.getCity(level, entry.getKey());
                double multiplier = getCityLevelMultiplier(city);
                double minPrice = Double.MAX_VALUE;
                for (RestaurantBoxData data : manager.all()) {
                    if (!data.running() || data.selectedCookItems().isEmpty()) continue;
                    if (!entity.blockPosition().closerThan(data.boxPos(), 256.0D)) continue;
                    PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
                    if (building == null) continue;
                    RestaurantDefinitionLoader.LoadResult lr = RestaurantDefinitionLoader.loadForBuilding(building);
                    if (!lr.valid() || lr.definition() == null) continue;
                    for (String itemId : data.selectedCookItems()) {
                        double price = lr.definition().cookPrice(itemId) * multiplier;
                        if (price < minPrice) minPrice = price;
                    }
                }
                if (minPrice == Double.MAX_VALUE) return false;
                return t.funds >= minPrice;
            }
        }
        return true;
    }

    private static double getCityLevelMultiplier(CityData city) {
        if (city == null) return 1.0;
        return switch (CityLevel.fromLevel(city.cityLevel())) {
            case SETTLEMENT, VILLAGE -> 1.0;
            case TOWN -> 1.5;
            case CITY_STATE -> 2.0;
            case METROPOLIS -> 2.5;
        };
    }

    private static void tickTouristArrivals(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % 20L != 0L) return;

        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            UUID cityId = entry.getKey();
            CityData city = CityDataService.getCity(level, cityId);
            if (city == null) continue;

            for (Tourist tourist : entry.getValue()) {
                syncTouristFundsDisplay(level, tourist);

                boolean nowDining = RestaurantDiningService.isDining(tourist.citizenId);
                if (nowDining) {
                    tourist.wasDining = true;
                    continue;
                }
                if (tourist.wasDining) {
                    tourist.wasDining = false;
                    tourist.lastDiningTick = gameTime;
                }

                if (gameTime - tourist.spawnTick >= TOURIST_LIFETIME_TICKS) {
                    applyTipAndRemove(level, city, tourist);
                    continue;
                }

                if (gameTime - tourist.lastWanderTick >= WANDER_INTERVAL) {
                    wanderTourist(level, cityId, tourist);
                }

                if (gameTime - tourist.lastDiningTick >= DINING_INTERVAL) {
                    tryRestaurantVisit(level, city, tourist, gameTime);
                }
            }
        }
    }

    @Nullable
    private static BlockPos pickGroundPos(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        if (!isOpenForCitizen(level, feet)) return null;
        return feet;
    }

    private static void applyTip(ServerLevel level, UUID cityId, Tourist tourist) {
        if (cityId == null || tourist == null || tourist.funds <= 0) return;
        long prosperity = CityProsperityCache.getOrCalculate(level, cityId);
        double ratio = Math.min(prosperity / 100.0D, TIP_MAX_RATIO * 100.0D) / 100.0D;
        double tip = tourist.funds * ratio;
        if (tip > 0) {
            EconomyService.depositCityFunds(level, cityId, null, tip, "tourist_tip", false);
        }
    }

    private static void applyTipAndRemove(ServerLevel level, CityData city, Tourist tourist) {
        applyTip(level, city != null ? city.cityId() : null, tourist);
        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity != null) {
            entity.discard();
        }
        CitizenManager.get(level).removeCitizen(tourist.citizenId);
        if (city != null) {
            CopyOnWriteArrayList<Tourist> tourists = ACTIVE_TOURISTS.get(city.cityId());
            if (tourists != null) {
                tourists.remove(tourist);
            }
        }
    }

    private static void syncTouristFundsDisplay(ServerLevel level, Tourist tourist) {
        if (RestaurantDiningService.isDining(tourist.citizenId)) return;
        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity == null) return;
        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;
        String funds = String.format("%.1f", tourist.funds);
        data.setStatusLabel("gui.xy2407_nsuk_addition.tourist.visiting||" + funds);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
    }

    private static void syncCaravanFundsDisplay(ServerLevel level, Caravan caravan) {
        CitizenEntity entity = findLoadedEntity(level, caravan.leaderId);
        if (entity == null) return;
        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;
        String funds = String.format("%.1f", caravan.funds);
        data.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS + "||" + funds);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
    }


    private static boolean isOpenForCitizen(ServerLevel level, BlockPos feet) {
        return feet != null
                && level.isEmptyBlock(feet)
                && level.isEmptyBlock(feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static void clearAllTourists(ServerLevel level) {
        CitizenManager manager = CitizenManager.get(level);
        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            UUID cityId = entry.getKey();
            for (Tourist tourist : entry.getValue()) {
                applyTip(level, cityId, tourist);
                manager.removeCitizen(tourist.citizenId);
            }
        }
        ACTIVE_TOURISTS.clear();

        discardByTag(level, TourismConstants.TOURIST_TAG, manager);

        clearAllCaravans(level);
    }

    private static void discardByTag(ServerLevel level, String tag, CitizenManager manager) {
        if (level == null || tag == null) return;
        for (Entity entity : level.getAllEntities()) {
            if (entity == null || !entity.getTags().contains(tag)) continue;
            if (manager != null) {
                manager.removeCitizen(entity.getUUID());
            }
            entity.discard();
        }
    }

    @Nullable
    private static Entity findAnyLoadedEntity(ServerLevel level, UUID entityId) {
        if (level == null || entityId == null) return null;
        return level.getEntity(entityId);
    }

    private static CitizenEntity findLoadedEntity(ServerLevel level, UUID citizenId) {
        Entity entity = findAnyLoadedEntity(level, citizenId);
        return entity instanceof CitizenEntity citizen ? citizen : null;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos core, UUID cityId) {
        RandomSource random = level.random;
        for (int i = 0; i < 10; i++) {
            int x = core.getX() + random.nextInt(7) - 3;
            int z = core.getZ() + random.nextInt(7) - 3;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, pos)) {
                return pos;
            }
        }
        return core.above();
    }

    private static final class Tourist {
        final UUID citizenId;
        double funds;
        final long spawnTick;
        long lastWanderTick;
        long lastDiningTick;
        int restaurantVisits;
        long lastRestaurantTick;
        boolean wasDining;

        Tourist(UUID citizenId, double funds, long spawnTick) {
            this.citizenId = citizenId;
            this.funds = funds;
            this.spawnTick = spawnTick;
            this.lastWanderTick = spawnTick;
            this.lastDiningTick = spawnTick;
            this.restaurantVisits = 0;
            this.lastRestaurantTick = 0;
            this.wasDining = false;
        }
    }


    private static final class Caravan {
        final UUID cityId;
        final UUID sourceCityId;
        final UUID leaderId;
        final List<UUID> followerIds;
        final List<UUID> muleIds;
        final List<CaravanProduct> products;
        final ConcurrentHashMap<String, Integer> productStock = new ConcurrentHashMap<>();
        double funds;
        BlockPos targetShop;
        int tradeCooldown;

        Caravan(UUID cityId, UUID sourceCityId, UUID leaderId, List<UUID> followerIds, List<UUID> muleIds,
                List<CaravanProduct> products, double funds) {
            this.cityId = cityId;
            this.sourceCityId = sourceCityId;
            this.leaderId = leaderId;
            this.followerIds = followerIds;
            this.muleIds = muleIds;
            this.products = products;
            for (CaravanProduct p : products) {
                productStock.put(p.itemId(), p.initStock());
            }
            this.funds = funds;
            this.targetShop = null;
            this.tradeCooldown = 0;
        }

        boolean isOnCooldown() { return tradeCooldown > 0; }
    }

    private static void spawnCaravanForCity(ServerLevel level, CityData playerCity, CityData sourceCity) {
        BlockPos core = playerCity.cityCorePos();
        if (core == null) return;
        UUID cityId = playerCity.cityId();

        BlockPos spawnPos = findSpawnPos(level, core, cityId);
        if (spawnPos == null) spawnPos = core.above();
        Vec3 center = Vec3.atBottomCenterOf(spawnPos);

        CityLevel sourceLevel = CityLevel.fromLevel(sourceCity.cityLevel());
        java.util.List<CaravanProduct> products = CaravanProductConfig.pickProducts(sourceLevel, level.random);

        double funds = switch (sourceLevel) {
            case SETTLEMENT -> 1000.0;
            case VILLAGE -> 3000.0;
            case TOWN -> 5000.0;
            case CITY_STATE -> 7000.0;
            case METROPOLIS -> 10000.0;
            default -> 1000.0;
        };

        CitizenEntity leader = ModEntities.CITIZEN.get().create(level);
        if (leader == null) return;
        leader.moveTo(center.x, center.y, center.z, level.random.nextFloat() * 360.0F, 0.0F);
        leader.setGlowingTag(true);
        leader.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS);
        leader.addTag(TourismConstants.CARAVAN_TAG);
        leader.addTag(TourismConstants.TRADE_TAG);
        level.addFreshEntity(leader);

        CitizenData leaderData = CitizenService.ensureCitizen(level, leader);
        if (leaderData != null) {
            leaderData.setCityId(null);
            leaderData.setJobType(CityJobType.UNEMPLOYED);
            leaderData.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS);
            CitizenService.save(level, leaderData.uuid());
            CitizenService.syncEntity(level, leader);
        }
        leader.setHunger(4.0F);

        List<UUID> followerIds = new ArrayList<>();
        List<UUID> muleIds = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            double side = i == 0 ? -1.0 : 1.0;
            Vec3 offset = center.add(side, 0, -2.0);
            CitizenEntity follower = ModEntities.CITIZEN.get().create(level);
            if (follower == null) continue;
            follower.moveTo(offset.x, offset.y, offset.z, level.random.nextFloat() * 360.0F, 0.0F);
            follower.setStatusLabel(TourismConstants.CARAVAN_FOLLOWER_STATUS);
            follower.addTag(TourismConstants.CARAVAN_TAG);
            level.addFreshEntity(follower);

            CitizenData fData = CitizenService.ensureCitizen(level, follower);
            if (fData != null) {
                fData.setCityId(null);
                fData.setJobType(CityJobType.UNEMPLOYED);
                fData.setStatusLabel(TourismConstants.CARAVAN_FOLLOWER_STATUS);
                CitizenService.save(level, fData.uuid());
                CitizenService.syncEntity(level, follower);
            }
            follower.setHunger(4.0F);
            followerIds.add(follower.getUUID());

            Vec3 muleOffset = center.add(side, 0, -1.0);
            Entity mule = EntityType.MULE.create(level);
            if (mule instanceof Mob mobMule) {
                mobMule.setPersistenceRequired();
                mobMule.moveTo(muleOffset.x, muleOffset.y, muleOffset.z, level.random.nextFloat() * 360.0F, 0.0F);
                mobMule.addTag(TourismConstants.CARAVAN_TAG);
                level.addFreshEntity(mobMule);
                mobMule.setLeashedTo(follower, true);
                muleIds.add(mobMule.getUUID());
            }
        }

        ACTIVE_CARAVANS.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>())
                .add(new Caravan(cityId, sourceCity.cityId(), leader.getUUID(), followerIds, muleIds, products, funds));
        persistCaravan(level, playerCity, sourceCity, leader.getUUID(), followerIds, muleIds, products, funds);
    }

    private static void tickCaravans(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (ACTIVE_CARAVANS.isEmpty()) return;

        boolean shouldSyncFunds = gameTime % 200L == 0L;
        boolean shouldFollow = gameTime % 40L == 0L;

        for (Map.Entry<UUID, List<Caravan>> entry : ACTIVE_CARAVANS.entrySet()) {
            List<Caravan> caravans = entry.getValue();
            if (caravans == null || caravans.isEmpty()) continue;

            for (Caravan caravan : caravans) {
                if (shouldSyncFunds) {
                    syncCaravanFundsDisplay(level, caravan);
                }
                if (shouldFollow) {
                    CitizenEntity leader = findLoadedEntity(level, caravan.leaderId);
                    if (leader == null) continue;
                    Vec3 leaderPos = leader.position();
                    for (UUID followerId : caravan.followerIds) {
                        CitizenEntity follower = findLoadedEntity(level, followerId);
                        if (follower == null) continue;
                        if (follower.distanceToSqr(leaderPos) > 9.0) {
                            CitizenNavigationService.requestMove(level, followerId, leaderPos, MovementIntent.WALK);
                        }
                    }
                }
            }
        }
    }

    private static void clearAllCaravans(ServerLevel level) {
        CitizenManager manager = CitizenManager.get(level);
        for (List<Caravan> caravans : ACTIVE_CARAVANS.values()) {
            for (Caravan caravan : caravans) {
                manager.removeCitizen(caravan.leaderId);
                for (UUID id : caravan.followerIds) {
                    manager.removeCitizen(id);
                }
            }
        }
        ACTIVE_CARAVANS.clear();
        discardByTag(level, TourismConstants.CARAVAN_TAG, manager);
        deletePersistedCaravans(level);
    }

    private static void persistCaravan(ServerLevel level, CityData playerCity, CityData sourceCity,
                                       UUID leaderId, List<UUID> followerIds, List<UUID> muleIds,
                                       List<CaravanProduct> products, double funds) {
        if (level == null || playerCity == null || sourceCity == null || leaderId == null) {
            return;
        }
        long boxPosLong = playerCity.cityCorePos().asLong();
        int caravanIndex = (leaderId.hashCode() & 0x7fffffff) % 1_000_000;
        int cityLevel = CityLevel.fromLevel(sourceCity.cityLevel()).level();
        long gameTime = level.getGameTime();
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submit(db, "foreign_trade_caravans",
                "caravan:" + boxPosLong + ":" + caravanIndex, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO foreign_trade_caravans"
                            + "(box_pos_long, caravan_index, name, leader_uuid, status, target_city_id,"
                            + " source_city_id, spawn_city_id, city_level, dimension, departure_day, funds) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(box_pos_long, caravan_index) DO UPDATE SET "
                            + "name = excluded.name, leader_uuid = excluded.leader_uuid, status = excluded.status,"
                            + "source_city_id = excluded.source_city_id, spawn_city_id = excluded.spawn_city_id,"
                            + "city_level = excluded.city_level, dimension = excluded.dimension,"
                            + "departure_day = excluded.departure_day, funds = excluded.funds")) {
                ps.setLong(1, boxPosLong);
                ps.setInt(2, caravanIndex);
                ps.setString(3, "caravan_" + caravanIndex);
                ps.setString(4, leaderId.toString());
                ps.setString(5, "idle");
                ps.setString(6, playerCity.cityId().toString());
                ps.setString(7, sourceCity.cityId().toString());
                ps.setString(8, playerCity.cityId().toString());
                ps.setInt(9, cityLevel);
                ps.setString(10, level.dimension().location().toString());
                ps.setLong(11, gameTime / 24000L);
                ps.setDouble(12, funds);
                ps.executeUpdate();
            }
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM foreign_trade_caravan_members WHERE box_pos_long = ? AND caravan_index = ?")) {
                del.setLong(1, boxPosLong);
                del.setInt(2, caravanIndex);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO foreign_trade_caravan_members"
                            + "(box_pos_long, caravan_index, citizen_uuid, citizen_name, role) "
                            + "VALUES(?,?,?,?,?)")) {
                insertMember(ps, boxPosLong, caravanIndex, leaderId, "leader");
                for (UUID follower : followerIds) {
                    insertMember(ps, boxPosLong, caravanIndex, follower, "follower");
                }
                for (UUID mule : muleIds) {
                    insertMember(ps, boxPosLong, caravanIndex, mule, "mule");
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO foreign_trade_caravan_products"
                            + "(box_pos_long, caravan_index, item_id, category, `limit`, stock) "
                            + "VALUES(?,?,?,?,?,?)")) {
                for (CaravanProduct product : products) {
                    ps.setLong(1, boxPosLong);
                    ps.setInt(2, caravanIndex);
                    ps.setString(3, product.itemId());
                    ps.setString(4, product.category());
                    ps.setInt(5, product.limit());
                    ps.setInt(6, product.initStock());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private static void insertMember(PreparedStatement ps, long boxPosLong, int caravanIndex,
                                     UUID uuid, String role) throws SQLException {
        ps.setLong(1, boxPosLong);
        ps.setInt(2, caravanIndex);
        ps.setString(3, uuid.toString());
        ps.setString(4, role);
        ps.setString(5, role);
        ps.addBatch();
    }

    public static void loadPersistedCaravans(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        try (Connection conn = db.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT box_pos_long, caravan_index, leader_uuid, source_city_id, spawn_city_id, funds "
                             + "FROM foreign_trade_caravans WHERE dimension = ?")) {
            ps.setString(1, dimension);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    loadOneCaravan(conn, level, rs);
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("Failed to load persisted caravans", e);
        }
        long today = level.getDayTime() / 24000L;
        for (Map.Entry<UUID, Long> e : DailyMarkerStorage.loadMarkers(level, "caravan_spawn").entrySet()) {
            if (e.getValue() == today) {
                LAST_CARAVAN_SPAWN_DAY_BY_CITY.put(e.getKey(), e.getValue());
            }
        }
    }

    private static void loadOneCaravan(Connection conn, ServerLevel level, ResultSet rs) throws SQLException {
        long boxPosLong = rs.getLong("box_pos_long");
        int caravanIndex = rs.getInt("caravan_index");
        String leaderUuidStr = rs.getString("leader_uuid");
        String spawnCityStr = rs.getString("spawn_city_id");
        String sourceCityStr = rs.getString("source_city_id");
        double funds = rs.getDouble("funds");
        if (leaderUuidStr == null || leaderUuidStr.isEmpty()
                || spawnCityStr == null || spawnCityStr.isEmpty()) {
            return;
        }
        UUID leaderId = UUID.fromString(leaderUuidStr);
        UUID cityId = UUID.fromString(spawnCityStr);
        UUID sourceCityId = (sourceCityStr == null || sourceCityStr.isEmpty())
                ? cityId : UUID.fromString(sourceCityStr);

        List<UUID> followerIds = new ArrayList<>();
        List<UUID> muleIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT citizen_uuid, role FROM foreign_trade_caravan_members "
                        + "WHERE box_pos_long = ? AND caravan_index = ?")) {
            ps.setLong(1, boxPosLong);
            ps.setInt(2, caravanIndex);
            try (ResultSet members = ps.executeQuery()) {
                while (members.next()) {
                    String role = members.getString("role");
                    String uuidStr = members.getString("citizen_uuid");
                    if (uuidStr == null || uuidStr.isEmpty()) continue;
                    UUID memberId = UUID.fromString(uuidStr);
                    if ("mule".equals(role)) {
                        muleIds.add(memberId);
                    } else if ("follower".equals(role)) {
                        followerIds.add(memberId);
                    }
                }
            }
        }

        List<CaravanProduct> products = new ArrayList<>();
        Map<String, Integer> stocks = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT item_id, category, `limit`, stock FROM foreign_trade_caravan_products "
                        + "WHERE box_pos_long = ? AND caravan_index = ?")) {
            ps.setLong(1, boxPosLong);
            ps.setInt(2, caravanIndex);
            try (ResultSet productRs = ps.executeQuery()) {
                while (productRs.next()) {
                    String itemId = productRs.getString("item_id");
                    if (itemId == null || itemId.isEmpty()) continue;
                    String category = productRs.getString("category");
                    int limit = productRs.getInt("limit");
                    products.add(new CaravanProduct(itemId, category == null ? "" : category, limit));
                    stocks.put(itemId, productRs.getInt("stock"));
                }
            }
        }
        if (products.isEmpty()) {
            return;
        }

        Caravan caravan = new Caravan(cityId, sourceCityId, leaderId, followerIds, muleIds, products, funds);
        stocks.forEach(caravan.productStock::put);
        ACTIVE_CARAVANS.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>()).add(caravan);
        long today = level.getDayTime() / 24000L;
        LAST_CARAVAN_SPAWN_DAY_BY_CITY.put(cityId, today);
        DailyMarkerStorage.save(level, cityId, "caravan_spawn", today);
    }

    private static void deletePersistedCaravans(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        NsukSqliteDatabase db = NsukSqliteDatabase.get(server);
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority(db, "foreign_trade_caravans", "caravan:clear", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM foreign_trade_caravans");
                 PreparedStatement ps2 = connection.prepareStatement("DELETE FROM foreign_trade_caravan_products")) {
                ps.executeUpdate();
                ps2.executeUpdate();
            }
        });
    }
}