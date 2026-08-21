package com.xy2407.nsukaddition.common.item;

import com.xy2407.nsukaddition.common.capture.EntityNbtSanitizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 生物捕获物品:右键生物将其收进物品(可堆叠多个实体),按 NBT 渲染实体模型。
 *  内部以 entries 列表按序号存储多个实体(1..N),支持跨 Stack 合并与按最大序号逐个释放。 */
public class EntityCaptureItem extends Item {

    public static final int MAX_CAPTURES = 64;

    public static final String TAG_ENTITY = "entity";
    public static final String TAG_ENTRIES = "entries";

    public EntityCaptureItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = getEntityType(stack);
        if (type != null) {
            return type.getDescription();
        }
        return super.getName(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }

    private static CompoundTag customTag(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            return new CompoundTag();
        }
        return stack.get(DataComponents.CUSTOM_DATA).copyTag();
    }

    private static void writeCustom(ItemStack stack, CompoundTag tag) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, cd -> CustomData.of(tag));
    }

    private static List<CompoundTag> readEntries(CompoundTag tag) {
        List<CompoundTag> result = new ArrayList<>();
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                result.add(list.getCompound(i).copy());
            }
        }
        return result;
    }

    private static void writeEntries(CompoundTag tag, List<CompoundTag> entries) {
        ListTag list = new ListTag();
        for (CompoundTag entry : entries) {
            list.add(entry.copy());
        }
        if (list.isEmpty()) {
            tag.remove(TAG_ENTRIES);
            tag.remove(TAG_ENTITY);
        } else {
            tag.put(TAG_ENTRIES, list);
        }
    }

    public static EntityType<?> getEntityType(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        String id = tag.getString(TAG_ENTITY);
        if (id.isEmpty()) {
            return null;
        }
        return EntityType.byString(id).orElse(null);
    }

    public static int getEntryCount(ItemStack stack) {
        if (!(stack.getItem() instanceof EntityCaptureItem)) {
            return 0;
        }
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        return entries.size();
    }

    public static boolean isEmpty(ItemStack stack) {
        return getEntryCount(stack) <= 0;
    }

    public static boolean isBaby(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        if (entries.isEmpty()) {
            return false;
        }
        return entries.get(entries.size() - 1).getInt("Age") < 0;
    }

    public static void setEntity(ItemStack stack, EntityType<?> type, boolean baby) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ENTITY, EntityType.getKey(type).toString());
        List<CompoundTag> entries = new ArrayList<>();
        CompoundTag entry = new CompoundTag();
        entry.putInt("Age", baby ? -24000 : 0);
        entries.add(entry);
        writeEntries(tag, entries);
        writeCustom(stack, tag);
    }

    public static boolean appendEntry(ItemStack stack, CompoundTag sanitizedNbt) {
        if (getEntryCount(stack) >= MAX_CAPTURES) {
            return false;
        }
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        entries.add(sanitizedNbt);
        writeEntries(tag, entries);
        writeCustom(stack, tag);
        return true;
    }

    private void clearCapture(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    public static boolean transferIn(ItemStack stack, EntityType<?> type, CompoundTag sanitizedNbt) {
        if (type == null || getEntryCount(stack) >= MAX_CAPTURES) {
            return false;
        }
        CompoundTag tag = customTag(stack);
        if (tag.getString(TAG_ENTITY).isEmpty()) {
            tag.putString(TAG_ENTITY, EntityType.getKey(type).toString());
        } else if (!tag.getString(TAG_ENTITY).equals(EntityType.getKey(type).toString())) {
            return false;
        }
        List<CompoundTag> entries = readEntries(tag);
        entries.add(sanitizedNbt);
        writeEntries(tag, entries);
        writeCustom(stack, tag);
        return true;
    }

    public static boolean mergeCaptures(ItemStack source, ItemStack target) {
        if (source.getCount() <= 0 || target.getCount() <= 0) {
            return false;
        }
        EntityType<?> sType = getEntityType(source);
        EntityType<?> tType = getEntityType(target);
        if (sType == null || tType == null || !sType.equals(tType)) {
            return false;
        }
        int targetCount = getEntryCount(target);
        if (targetCount >= MAX_CAPTURES) {
            return false;
        }
        CompoundTag sTag = customTag(source);
        CompoundTag tTag = customTag(target);
        List<CompoundTag> sEntries = readEntries(sTag);
        List<CompoundTag> tEntries = readEntries(tTag);
        int space = MAX_CAPTURES - targetCount;
        int take = Math.min(sEntries.size(), space);
        if (take <= 0) {
            return false;
        }
        for (int i = sEntries.size() - take; i < sEntries.size(); i++) {
            tEntries.add(sEntries.get(i).copy());
        }
        List<CompoundTag> sRemain = new ArrayList<>();
        for (int i = 0; i < sEntries.size() - take; i++) {
            sRemain.add(sEntries.get(i).copy());
        }
        writeEntries(tTag, tEntries);
        writeCustom(target, tTag);
        writeEntries(sTag, sRemain);
        writeCustom(source, sTag);
        return take > 0;
    }

    private static boolean tryMergeStack(ItemStack a, ItemStack b) {
        if (!(a.getItem() instanceof EntityCaptureItem) || !(b.getItem() instanceof EntityCaptureItem)) {
            return false;
        }
        EntityType<?> ta = getEntityType(a);
        EntityType<?> tb = getEntityType(b);
        if (ta == null || tb == null || !ta.equals(tb)) {
            return false;
        }
        ItemStack src = getEntryCount(a) <= getEntryCount(b) ? a : b;
        ItemStack tgt = src == a ? b : a;
        return mergeCaptures(src, tgt);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, net.minecraft.world.inventory.Slot slot,
                                          net.minecraft.world.inventory.ClickAction clickAction,
                                          net.minecraft.world.entity.player.Player player) {
        if (clickAction != net.minecraft.world.inventory.ClickAction.PRIMARY) {
            return false;
        }
        if (tryMergeStack(stack, slot.getItem())) {
            slot.setChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other,
                                            net.minecraft.world.inventory.Slot slot,
                                            net.minecraft.world.inventory.ClickAction clickAction,
                                            net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.entity.SlotAccess slotAccess) {
        if (clickAction != net.minecraft.world.inventory.ClickAction.PRIMARY) {
            return false;
        }
        if (tryMergeStack(stack, other)) {
            slot.setChanged();
            return true;
        }
        return false;
    }

    public static Optional<CompoundTag> popEntry(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag removed = entries.get(entries.size() - 1);
        entries.remove(entries.size() - 1);
        writeEntries(tag, entries);
        writeCustom(stack, tag);
        return Optional.of(removed);
    }

    public static int removeEntries(ItemStack stack, int amount) {
        if (amount <= 0) {
            return 0;
        }
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        int removed = Math.min(amount, entries.size());
        for (int i = 0; i < removed; i++) {
            entries.remove(entries.size() - 1);
        }
        writeEntries(tag, entries);
        writeCustom(stack, tag);
        return removed;
    }

    public static boolean isCompatibleCapture(ItemStack stack, EntityType<?> type) {
        if (type == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof EntityCaptureItem)) {
            return false;
        }
        EntityType<?> own = getEntityType(stack);
        return own != null && own.equals(type);
    }

    public static int fillEntries(ItemStack stack, int amount, boolean baby) {
        if (amount <= 0) {
            return 0;
        }
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        int space = MAX_CAPTURES - entries.size();
        int add = Math.min(space, amount);
        for (int i = 0; i < add; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Age", baby ? -24000 : 0);
            entries.add(entry);
        }
        writeEntries(tag, entries);
        writeCustom(stack, tag);
        return add;
    }

    public static ItemStack createCapture(ItemStack seed, EntityType<?> type, boolean baby, int count) {
        ItemStack device = seed.copy();
        if (getEntityType(device) == null) {
            setEntity(device, type, baby);
        }
        int need = Math.max(0, count - getEntryCount(device));
        fillEntries(device, need, baby);
        return device;
    }

    public static int distributeCapture(List<ItemStack> slots, ItemStack seed, EntityType<?> type, boolean baby, int count) {
        if (type == null || slots == null) {
            return Math.max(0, count);
        }
        int remaining = Math.max(0, count);
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (!isCompatibleCapture(slot, type)) {
                continue;
            }
            remaining -= fillEntries(slot, remaining, baby);
        }
        while (remaining > 0) {
            int free = -1;
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) == null || slots.get(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            if (free < 0) {
                break;
            }
            int chunk = Math.min(remaining, MAX_CAPTURES);
            slots.set(free, createCapture(seed, type, baby, chunk));
            remaining -= chunk;
        }
        return remaining;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = getEntityType(stack);
        if (type == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clickPos = context.getClickedPos();
        BlockState clickState = level.getBlockState(clickPos);
        BlockPos spawnPos = clickState.is(Blocks.WATER) ? clickPos : clickPos.relative(context.getClickedFace());
        if (!level.getBlockState(spawnPos).canBeReplaced()) {
            spawnPos = spawnPos.above();
        }
        if (!level.getBlockState(spawnPos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        CompoundTag tag = customTag(stack);
        List<CompoundTag> entries = readEntries(tag);
        if (entries.isEmpty()) {
            return InteractionResult.FAIL;
        }
        CompoundTag entry = entries.get(entries.size() - 1);
        Entity entity = type.create(serverLevel);
        if (entity == null) {
            return InteractionResult.FAIL;
        }
        try {
            entity.load(entry.copy());
        } catch (RuntimeException ignored) {
        }
        entity.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                (float) (Math.random() * 360.0), 0.0F);
        if (!serverLevel.addFreshEntity(entity)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        if (player != null && player.getAbilities().instabuild) {
            return InteractionResult.SUCCESS;
        }
        popEntry(stack);
        if (getEntryCount(stack) == 0) {
            clearCapture(stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof common.cn.kafei.simukraft.entity.CitizenEntity
                || target instanceof com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity) {
            return InteractionResult.PASS;
        }
        if (!com.xy2407.nsukaddition.common.capture.CapturableEntityRegistry.isCapturable(target)) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Mob mob)) {
            return InteractionResult.PASS;
        }
        if (mob.getPersistentData().contains("nsuk_breeding_entries")) {
            if (player.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            boolean moved = com.xy2407.nsukaddition.common.breeding.BreedingWorkService.baseToCapture(
                    (net.minecraft.server.level.ServerLevel) player.level(), player, hand, mob);
            return moved ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        EntityType<?> existingForCapture = getEntityType(stack);
        if (existingForCapture != null && !existingForCapture.equals(mob.getType())) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return captureFromHand(player, mob, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public boolean captureFromHand(Player player, Mob mob, InteractionHand hand) {
        if (player.level().isClientSide() || !mob.isAlive()) {
            return false;
        }
        if (mob instanceof common.cn.kafei.simukraft.entity.CitizenEntity) {
            return false;
        }
        if (!com.xy2407.nsukaddition.common.capture.CapturableEntityRegistry.isCapturable(mob)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        EntityType<?> stackType = getEntityType(stack);
        if (stackType != null && !stackType.equals(mob.getType())) {
            return false;
        }
        CompoundTag raw = new CompoundTag();
        mob.saveWithoutId(raw);
        CompoundTag entry = EntityNbtSanitizer.sanitize(raw);

        if (stackType != null && getEntryCount(stack) < MAX_CAPTURES) {
            appendEntry(stack, entry);
            mob.discard();
            return true;
        }

        ItemStack captured = new ItemStack(this);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ENTITY, EntityType.getKey(mob.getType()).toString());
        List<CompoundTag> list = new ArrayList<>();
        list.add(entry);
        writeEntries(tag, list);
        writeCustom(captured, tag);
        mob.discard();

        if (player.getAbilities().instabuild) {
            if (!player.getInventory().add(captured)) {
                player.drop(captured, false);
            }
        } else if (stack.getCount() == 1 && getEntryCount(stack) == 0) {
            player.setItemInHand(hand, captured);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(captured)) {
                player.drop(captured, false);
            }
        }
        return true;
    }
}
