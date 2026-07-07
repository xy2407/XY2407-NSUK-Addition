package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.SidebarSyncBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 侧边栏同步网络包，将城市数据（官员、建筑、物资、任务、财务、市民）同步到客户端。 */
public record SidebarSyncPacket(
        UUID cityId,
        List<String> officerNames,
        List<String> officerPerms,
        int shopCount,
        int factoryCount,
        int residenceCount,
        int farmCount,
        int ranchCount,
        int mineCount,
        List<MaterialEntry> reserveMaterials,
        List<BuildTaskData> buildTasks,
        List<FinanceEntry> financeEntries,
        List<CitizenEntry> citizens
) implements CustomPacketPayload {

    public static final Type<SidebarSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "sidebar_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SidebarSyncPacket> STREAM_CODEC =
            StreamCodec.of(SidebarSyncPacket::encode, SidebarSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MaterialEntry(String categoryKey, int count) {
    }

    public record BuildTaskData(
            String displayName,
            String citizenId,
            int progressPercent,
            String statusKey,
            boolean tracked,
            List<MaterialEntry> required,
            List<MaterialEntry> available
    ) {
    }

    public record FinanceEntry(
            long time,
            String actorName,
            double amount,
            double balanceAfter,
            String type,
            String reason
    ) {
    }

    public record CitizenEntry(
            String name,
            String uuid,
            String jobType,
            boolean hasHome,
            String skinPath
    ) {
    }

    public static void encode(RegistryFriendlyByteBuf b, SidebarSyncPacket p) {
        b.writeBoolean(p.cityId != null);
        if (p.cityId != null) b.writeUUID(p.cityId);
        writeStrList(b, p.officerNames);
        writeStrList(b, p.officerPerms);
        b.writeInt(p.shopCount);
        b.writeInt(p.factoryCount);
        b.writeInt(p.residenceCount);
        b.writeInt(p.farmCount);
        b.writeInt(p.ranchCount);
        b.writeInt(p.mineCount);
        writeMaterials(b, p.reserveMaterials);
        b.writeVarInt(p.buildTasks.size());
        for (BuildTaskData task : p.buildTasks) {
            b.writeUtf(task.displayName(), 128);
            b.writeUtf(task.citizenId, 36);
            b.writeVarInt(task.progressPercent);
            b.writeUtf(task.statusKey, 64);
            b.writeBoolean(task.tracked);
            writeMaterials(b, task.required);
            writeMaterials(b, task.available);
        }

        b.writeVarInt(p.financeEntries.size());
        for (FinanceEntry e : p.financeEntries) {
            b.writeVarLong(e.time);
            b.writeUtf(e.actorName, 64);
            b.writeDouble(e.amount);
            b.writeDouble(e.balanceAfter);
            b.writeUtf(e.type, 16);
            b.writeUtf(e.reason, 64);
        }

        b.writeVarInt(p.citizens.size());
        for (CitizenEntry ce : p.citizens) {
            b.writeUtf(ce.name, 64);
            b.writeUtf(ce.uuid, 36);
            b.writeUtf(ce.jobType, 32);
            b.writeBoolean(ce.hasHome);
            b.writeUtf(ce.skinPath, 128);
        }
    }

    public static SidebarSyncPacket decode(RegistryFriendlyByteBuf b) {
        UUID cityId = b.readBoolean() ? b.readUUID() : null;
        List<String> officerNames = readStrList(b);
        List<String> officerPerms = readStrList(b);
        int shopCount = b.readInt();
        int factoryCount = b.readInt();
        int residenceCount = b.readInt();
        int farmCount = b.readInt();
        int ranchCount = b.readInt();
        int mineCount = b.readInt();
        List<MaterialEntry> reserveMaterials = readMaterials(b);

        int taskCount = b.readVarInt();
        List<BuildTaskData> buildTasks = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            String displayName = b.readUtf(128);
            String citizenId = b.readUtf(36);
            int progress = b.readVarInt();
            String status = b.readUtf(64);
            boolean tracked = b.readBoolean();
            List<MaterialEntry> required = readMaterials(b);
            List<MaterialEntry> available = readMaterials(b);
            buildTasks.add(new BuildTaskData(displayName, citizenId, progress, status, tracked, required, available));
        }

        int financeCount = b.readVarInt();
        List<FinanceEntry> financeEntries = new ArrayList<>(financeCount);
        for (int i = 0; i < financeCount; i++) {
            long time = b.readVarLong();
            String actorName = b.readUtf(64);
            double amount = b.readDouble();
            double balanceAfter = b.readDouble();
            String type = b.readUtf(16);
            String reason = b.readUtf(64);
            financeEntries.add(new FinanceEntry(time, actorName, amount, balanceAfter, type, reason));
        }

        int citizenCount = b.readVarInt();
        List<CitizenEntry> citizens = new ArrayList<>(citizenCount);
        for (int i = 0; i < citizenCount; i++) {
            String name = b.readUtf(64);
            String uuid = b.readUtf(36);
            String jobType = b.readUtf(32);
            boolean hasHome = b.readBoolean();
            String skinPath = b.readUtf(128);
            citizens.add(new CitizenEntry(name, uuid, jobType, hasHome, skinPath));
        }

        return new SidebarSyncPacket(
                cityId, officerNames, officerPerms,
                shopCount, factoryCount, residenceCount, farmCount, ranchCount, mineCount,
                reserveMaterials, buildTasks, financeEntries, citizens);
    }

    public static void handle(SidebarSyncPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SidebarSyncBridge.handle(p));
    }

    private static void writeMaterials(RegistryFriendlyByteBuf b, List<MaterialEntry> materials) {
        b.writeVarInt(materials.size());
        for (MaterialEntry entry : materials) {
            b.writeUtf(entry.categoryKey, 64);
            b.writeVarInt(entry.count);
        }
    }

    private static List<MaterialEntry> readMaterials(RegistryFriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n == 0) {
            return Collections.emptyList();
        }
        List<MaterialEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String key = b.readUtf(64);
            int count = b.readVarInt();
            list.add(new MaterialEntry(key, count));
        }
        return list;
    }

    private static void writeStrList(RegistryFriendlyByteBuf b, List<String> l) {
        b.writeVarInt(l.size());
        for (String s : l) {
            b.writeUtf(s, 128);
        }
    }

    private static List<String> readStrList(RegistryFriendlyByteBuf b) {
        int n = b.readVarInt();
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(b.readUtf(128));
        }
        return l;
    }
}
