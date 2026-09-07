package com.xy2407.nsukaddition.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 城市对话 NPC：玩家建城后出现，免疫伤害、不攻击，在城市核心周围7格内按固定间隔移动，右键打开对话框。 */
public class DialogNpcEntity extends PathfinderMob {

    public static final EntityType<DialogNpcEntity> TYPE = EntityType.Builder
            .<DialogNpcEntity>of(DialogNpcEntity::new, MobCategory.MISC)
            .sized(0.6F, 1.8F).clientTrackingRange(8).updateInterval(1).build("dialog_npc");

    private static final long REPATH_INTERVAL = 1200L;
    private static final int CORE_RANGE = 7;
    private static final double MOVE_SPEED = 0.45D;

    private BlockPos coreOrigin;
    private UUID lookTargetId;
    private boolean patrolEnabled = true;
    private final Map<String, Integer> shopStock = new ConcurrentHashMap<>();

    public DialogNpcEntity(EntityType<DialogNpcEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    public void setCoreOrigin(BlockPos pos) {
        this.coreOrigin = pos == null ? null : pos.immutable();
    }

    public BlockPos getCoreOrigin() {
        return this.coreOrigin;
    }

    public void setPatrol(boolean enabled) {
        if (!enabled) {
            this.getNavigation().stop();
        }
        this.patrolEnabled = enabled;
    }

    public boolean isPatrolEnabled() {
        return this.patrolEnabled;
    }

    /** 打开对话框时暂停当前移动，但不改变玩家选择的"待在原地/巡逻"状态，避免关闭后丢失选择。 */
    public void pauseForDialog() {
        this.getNavigation().stop();
    }

    public int getShopStock(String key) {
        return this.shopStock.getOrDefault(key, 0);
    }

    public boolean hasShopStock(String key) {
        return this.shopStock.containsKey(key);
    }

    public void setShopStock(String key, int count) {
        this.shopStock.put(key, Math.max(0, count));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (this.lookTargetId == null) {
                this.lookTargetId = serverPlayer.getUUID();
            }
            this.pauseForDialog();
            com.xy2407.nsukaddition.server.city.DialogNpcDialogService.openFor(
                    (net.minecraft.server.level.ServerLevel) this.level(), serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.coreOrigin == null) {
            return;
        }
        if (this.lookTargetId != null) {
            Entity target = ((net.minecraft.server.level.ServerLevel) this.level()).getPlayerByUUID(this.lookTargetId);
            if (target instanceof Player player && this.distanceToSqr(player) < 144.0D
                    && this.getNavigation().isDone()) {
                this.getLookControl().setLookAt(player, 10.0F, 20.0F);
            } else {
                this.lookTargetId = null;
            }
        }
        if (this.tickCount == 0 || this.tickCount % REPATH_INTERVAL != 0 || !this.patrolEnabled) {
            return;
        }
        int dx = this.random.nextInt(CORE_RANGE * 2 + 1) - CORE_RANGE;
        int dz = this.random.nextInt(CORE_RANGE * 2 + 1) - CORE_RANGE;
        int targetX = this.coreOrigin.getX() + dx;
        int targetZ = this.coreOrigin.getZ() + dz;
        int groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
        this.getNavigation().moveTo(targetX + 0.5D, groundY, targetZ + 0.5D, MOVE_SPEED);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.coreOrigin != null) {
            tag.putInt("dialog_core_x", this.coreOrigin.getX());
            tag.putInt("dialog_core_y", this.coreOrigin.getY());
            tag.putInt("dialog_core_z", this.coreOrigin.getZ());
        }
        tag.putBoolean("dialog_patrol", this.patrolEnabled);
        if (!this.shopStock.isEmpty()) {
            CompoundTag stock = new CompoundTag();
            for (Map.Entry<String, Integer> e : this.shopStock.entrySet()) {
                stock.putInt(e.getKey(), e.getValue());
            }
            tag.put("dialog_stock", stock);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("dialog_core_x")) {
            this.coreOrigin = new BlockPos(tag.getInt("dialog_core_x"), tag.getInt("dialog_core_y"), tag.getInt("dialog_core_z"));
        }
        if (tag.contains("dialog_patrol")) {
            this.patrolEnabled = tag.getBoolean("dialog_patrol");
        }
        if (tag.contains("dialog_stock")) {
            CompoundTag stock = tag.getCompound("dialog_stock");
            for (String key : stock.getAllKeys()) {
                this.shopStock.put(key, stock.getInt(key));
            }
        }
    }
}