package com.xy2407.nsukaddition.common.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 餐厅控制箱数据，存储位置、配方、运行状态、订单队列与座位占用信息。 */
@SuppressWarnings("null")
public final class RestaurantBoxData {
    private final BlockPos boxPos;
    private String buildingId = "";
    private String definitionId = "";
    private boolean running;
    private String statusKey = "";
    private String statusText = "";
    private int progressTicks;
    private String selectedRecipeId = "";
    private int cooldownTicks;
    private String workState = "";
    private long updatedAt;
    private final List<OrderEntry> orders = new ArrayList<>();
    private final Set<BlockPos> occupiedSeats = new HashSet<>();
    private Set<String> selectedCookItems = new HashSet<>();
    private final List<MaidEntry> maidWaiters = new ArrayList<>();

    public RestaurantBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public BlockPos boxPos() { return boxPos; }
    public String buildingId() { return buildingId; }
    public void setBuildingId(String v) { this.buildingId = v != null ? v : ""; }
    public String definitionId() { return definitionId; }
    public void setDefinitionId(String v) { this.definitionId = v != null ? v : ""; }
    public boolean running() { return running; }
    public void setRunning(boolean v) { this.running = v; }
    public String statusKey() { return statusKey; }
    public void setStatusKey(String v) { this.statusKey = v != null ? v : ""; }
    public String statusText() { return statusText; }
    public void setStatusText(String v) { this.statusText = v != null ? v : ""; }
    public int progressTicks() { return progressTicks; }
    public void setProgressTicks(int v) { this.progressTicks = Math.max(0, v); }
    public long updatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = System.currentTimeMillis(); }
    public String selectedRecipeId() { return selectedRecipeId; }
    public void setSelectedRecipeId(String v) { this.selectedRecipeId = v != null ? v : ""; }
    public int cooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int v) { this.cooldownTicks = Math.max(0, v); }
    public String workState() { return workState; }
    public void setWorkState(String v) { this.workState = v != null ? v : ""; }
    public Set<String> selectedCookItems() { return selectedCookItems; }
    public void setSelectedCookItems(Set<String> v) { this.selectedCookItems = v != null ? new HashSet<>(v) : new HashSet<>(); }

    public List<OrderEntry> orders() { return orders; }
    public void addOrder(UUID customerId, BlockPos seatPos, String recipeId) { orders.add(new OrderEntry(customerId, seatPos, recipeId)); }
    public void removeOrder(UUID customerId) { orders.removeIf(o -> o.customerId().equals(customerId)); }

    public OrderEntry nextPendingOrder() {
        for (OrderEntry o : orders) { if (o.status() == OrderStatus.PENDING) return o; }
        return null;
    }

    public void occupySeat(BlockPos worldSeat) { occupiedSeats.add(worldSeat.immutable()); }

    public void freeSeat(BlockPos worldSeat) { occupiedSeats.remove(worldSeat.immutable()); }

    public boolean isSeatFree(BlockPos worldSeat) { return !occupiedSeats.contains(worldSeat.immutable()); }

    public int occupiedSeatCount() { return occupiedSeats.size(); }

    public boolean hasFreeSeats() {
        return true;
    }

    public List<MaidEntry> maidWaiters() {
        return List.copyOf(maidWaiters);
    }

    public boolean hasMaid(UUID maidId) {
        if (maidId == null) return false;
        return maidWaiters.stream().anyMatch(m -> m.uuid().equals(maidId));
    }

    public void addMaid(UUID maidId, String name) {
        if (maidId == null || hasMaid(maidId)) return;
        maidWaiters.add(new MaidEntry(maidId, name != null ? name : ""));
    }

    public boolean removeMaid(UUID maidId) {
        if (maidId == null) return false;
        return maidWaiters.removeIf(m -> m.uuid().equals(maidId));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putString("BuildingId", buildingId);
        tag.putString("DefinitionId", definitionId);
        tag.putBoolean("Running", running);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putLong("UpdatedAt", updatedAt);
        ListTag list = new ListTag();
        for (OrderEntry o : orders) {
            CompoundTag ot = new CompoundTag();
            ot.putUUID("Customer", o.customerId());
            ot.putLong("Seat", o.seatPos().asLong());
            ot.putString("Recipe", o.recipeId());
            ot.putString("Status", o.status().name());
            list.add(ot);
        }
        tag.put("Orders", list);
        ListTag cookList = new ListTag();
        for (String item : selectedCookItems) { cookList.add(net.minecraft.nbt.StringTag.valueOf(item)); }
        tag.put("SelectedCookItems", cookList);
        ListTag maidList = new ListTag();
        for (MaidEntry m : maidWaiters) {
            CompoundTag mt = new CompoundTag();
            mt.putUUID("MaidId", m.uuid());
            mt.putString("MaidName", m.name());
            maidList.add(mt);
        }
        tag.put("MaidWaiters", maidList);
        return tag;
    }

    public static RestaurantBoxData fromTag(CompoundTag tag) {
        RestaurantBoxData data = new RestaurantBoxData(BlockPos.of(tag.getLong("BoxPos")));
        data.buildingId = tag.getString("BuildingId");
        data.definitionId = tag.getString("DefinitionId");
        data.running = tag.getBoolean("Running");
        data.statusKey = tag.getString("StatusKey");
        data.statusText = tag.getString("StatusText");
        data.progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        data.updatedAt = tag.getLong("UpdatedAt");
        ListTag list = tag.getList("Orders", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ot = list.getCompound(i);
            UUID cid = ot.getUUID("Customer");
            BlockPos seat = BlockPos.of(ot.getLong("Seat"));
            String rid = ot.getString("Recipe");
            String statusStr = ot.getString("Status");
            OrderStatus st = OrderStatus.PENDING;
            try { st = OrderStatus.valueOf(statusStr); } catch (Exception ignored) {}
            data.orders.add(new OrderEntry(cid, seat, rid, st));
        }
        ListTag cookList = tag.getList("SelectedCookItems", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < cookList.size(); i++) { data.selectedCookItems.add(cookList.getString(i)); }
        ListTag maidList = tag.getList("MaidWaiters", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < maidList.size(); i++) {
            CompoundTag mt = maidList.getCompound(i);
            UUID maidId = mt.getUUID("MaidId");
            if (maidId != null) data.maidWaiters.add(new MaidEntry(maidId, mt.getString("MaidName")));
        }
        return data;
    }

    public record MaidEntry(UUID uuid, String name) {
        public MaidEntry {
            uuid = uuid != null ? uuid : UUID.randomUUID();
            name = name != null ? name : "";
        }
    }

    public record OrderEntry(UUID customerId, BlockPos seatPos, String recipeId, OrderStatus status) {
        public OrderEntry(UUID customerId, BlockPos seatPos, String recipeId) { this(customerId, seatPos, recipeId, OrderStatus.PENDING); }
    }

    public enum OrderStatus { PENDING, COOKING, COOKED, SERVING, DONE }
}
