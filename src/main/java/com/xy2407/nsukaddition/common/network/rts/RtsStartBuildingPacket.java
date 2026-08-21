package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.storage.BuildingTaskQueueStorage;
import com.xy2407.nsukaddition.server.building.BuildTaskTrackedState;
import com.xy2407.nsukaddition.server.building.BuildingTaskQueueService;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.building.BuildingTaskStatus;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
import common.cn.kafei.simukraft.building.BuilderConstructionMobilityService;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RTS 放置建筑请求：玩家选中建筑师 NPC 后在底部列表选择建筑，右键确认时发送，
 * 服务端根据建筑师反查其建筑盒、校验城市领地与资金、扣款并启动建造任务（复用物流仓库取料与建造队列）。
 */
public record RtsStartBuildingPacket(UUID builderId,
                                     String category,
                                     String buildingFileName,
                                     BlockPos origin,
                                     int rotationDegrees) implements CustomPacketPayload {
    public static final Type<RtsStartBuildingPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_start_building"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsStartBuildingPacket> STREAM_CODEC = StreamCodec.of(RtsStartBuildingPacket::encode, RtsStartBuildingPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RtsStartBuildingPacket packet) {
        buffer.writeUUID(packet.builderId());
        buffer.writeUtf(packet.category(), 32);
        buffer.writeUtf(packet.buildingFileName(), 256);
        buffer.writeBlockPos(packet.origin());
        buffer.writeInt(packet.rotationDegrees());
    }

    public static RtsStartBuildingPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsStartBuildingPacket(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(256), buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(RtsStartBuildingPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, packet.builderId());
        if (citizenOptional.isEmpty()) {
            sendResult(player, packet, false, "citizen_not_found");
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return;
        }
        CitizenData citizen = citizenOptional.get();
        if (citizen.dead() || citizen.jobType() != CityJobType.BUILDER) {
            sendResult(player, packet, false, "citizen_not_found");
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return;
        }
        UUID cityId = citizen.cityId();
        BlockPos boxPos = citizen.workplacePos();
        if (boxPos == null || citizen.workplaceId() == null
                || !CitizenEmploymentService.workplaceId("build_box", "builder", boxPos).equals(citizen.workplaceId())) {
            sendResult(player, packet, false, "build_box_not_found");
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
            return;
        }
        if (!level.getBlockState(boxPos).is(common.cn.kafei.simukraft.registry.ModBlocks.BUILD_BOX.get())) {
            sendResult(player, packet, false, "build_box_not_found");
            InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
            return;
        }
        if (!CityService.canManageCity(level, cityId, player.getUUID())) {
            sendResult(player, packet, false, "no_permission");
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.no_permission"));
            return;
        }
        Optional<BuildingStructure> structureOptional = BuildingStructureService.loadStructure(packet.category(), packet.buildingFileName());
        if (structureOptional.isEmpty()) {
            sendResult(player, packet, false, "structure_not_found");
            InfoToastService.error(player, Component.translatable("message.simukraft.build_box.structure_not_found"));
            return;
        }
        BuildingStructure structure = structureOptional.get();
        List<BuildingBlockData> placedBlocks = BuildingStructureService.resolvePlacedBlocks(structure, packet.origin(), packet.rotationDegrees());
        if (ServerConfig.claimProtectionEnabled() && !BuildingTerritoryValidator.blockBoundsInCity(level, cityId, placedBlocks)) {
            sendResult(player, packet, false, "outside_city");
            InfoToastService.warning(player, Component.translatable("message.simukraft.construction.outside_city"));
            return;
        }
        double constructionCost = EconomyService.parseAmount(structure.amount(), "construction");
        if (constructionCost > 0.0D) {
            if (!EconomyService.canAfford(level, cityId, constructionCost) || !CityService.withdrawFunds(level, cityId, constructionCost)) {
                sendResult(player, packet, false, "not_enough_funds");
                InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.not_enough_funds", constructionCost));
                return;
            }
            FinanceLedgerService.record(level, cityId, player, -constructionCost, EconomyService.getCityBalance(level, cityId), FinanceTransactionData.Type.EXPENSE, "construction");
            HudSyncService.syncToCityGroup(level, cityId, true);
        }
        if (BuildingTaskQueueStorage.countByCitizen(level, citizen.uuid()) >= 256) {
            sendResult(player, packet, false, "task_limit_reached");
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.task_limit_reached"));
            return;
        }
        long now = System.currentTimeMillis();
        BuildingTaskData task = new BuildingTaskData(
                UUID.randomUUID(),
                citizen.uuid(),
                cityId,
                level.dimension().location().toString(),
                boxPos,
                packet.category(),
                packet.buildingFileName(),
                structure.displayName(),
                structure.amount(),
                structure.structureFileName(),
                packet.origin(),
                packet.rotationDegrees(),
                0,
                structure.blockCount(),
                BuildingTaskStatus.QUEUED.id(),
                now,
                now,
                structure.poiDefinitions(),
                false
        );
        if (BuildingTaskQueueService.hasRunningTask(level, boxPos)) {
            BuildingTaskQueueService.enqueue(level, task);
            BuildingTaskQueueStorage.flush(level, task.taskId());
        } else {
            if (task.cityId() != null) {
                BuildTaskTrackedState.setTrackedTask(level, task.cityId(), task.taskId());
            }
            BuilderConstructionService.startTask(level, task.withStatus(BuildingTaskStatus.BUILDING));
            BuildingTaskQueueStorage.flushRunning(level, task.citizenId());
        }
        String statusLabel = Component.Serializer.toJson(
                Component.translatable("status.simukraft.builder.building", structure.displayName()),
                level.registryAccess());
        CitizenEmploymentService.assign(level, citizen.uuid(), CityJobType.BUILDER,
                CitizenEmploymentService.workplaceId("build_box", "builder", boxPos), boxPos, CitizenWorkStatus.WORKING, statusLabel);
        BuilderConstructionMobilityService.prepareForConstruction(level, citizen.uuid(), boxPos);
        citizen.setWorkNeedDetail("build:" + task.taskId());
        citizen.setStatusLabel(statusLabel);
        CitizenService.save(level, citizen.uuid());
        CityGroupMessageService.successToCity(level, cityId, Component.translatable("message.simukraft.build_box.construction_started", structure.displayName()));
        com.xy2407.nsukaddition.server.SidebarDataCache.refreshAsync(level);
        sendResult(player, packet, true, "");
    }

    private static void sendResult(ServerPlayer player, RtsStartBuildingPacket p, boolean success, String reason) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new RtsStartBuildingResultPacket(p.category(), p.origin(), p.rotationDegrees(), success, reason));
    }
}
