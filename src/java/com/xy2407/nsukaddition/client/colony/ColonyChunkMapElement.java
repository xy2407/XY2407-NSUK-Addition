package com.xy2407.nsukaddition.client.colony;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkAbandonPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkBuyPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 附属地地图渲染控件，参照 CityChunkMapElement 实现真实世界地形渲染、缩放、拖动和右键购买区块。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ColonyChunkMapElement extends UIElement {

    private static final double MIN_ZOOM = 0.5D;
    private static final double MAX_ZOOM = 10.0D;
    private static final double ZOOM_STEP = 0.5D;
    private static final int MAP_SIDE_PADDING = 4;
    private static final int MAP_TOP_PADDING = 4;
    private static final int SELF_BORDER_COLOR = 0xCC00DD00;
    private static final int SELF_CHUNK_FILL_COLOR = 0x5500DD00;
    private static final int ALLY_BORDER_COLOR = 0xCC4488FF;
    private static final int ALLY_CHUNK_FILL_COLOR = 0x554488FF;
    private static final int ENEMY_BORDER_COLOR = 0xCCFF2222;
    private static final int ENEMY_CHUNK_FILL_COLOR = 0x55FF2222;
    private static final int GRID_COLOR = 0x40000000;
    private static final int CORE_MARKER_COLOR = 0xFF4080FF;
    private static final int ADJACENT_BUYABLE_COLOR = 0x66FFFF55;

    private final ColonyCoreOpenResponsePacket packet;
    private final ClientCityChunkCache cityCache = ClientCityChunkCache.getInstance();
    private final ColonyChunkClientCache colonyCache = ColonyChunkClientCache.getInstance();
    private final SimuMapManager mapManager = SimuMapManager.getInstance();
    private final Set<Long> selfChunks = new HashSet<>();
    private double zoomLevel = 4.0D;
    private double offsetX;
    private double offsetY;
    private int contextMenuChunkX;
    private int contextMenuChunkZ;
    private double contextMenuX;
    private double contextMenuY;
    private int contextMenuDrawX;
    private int contextMenuDrawY;
    private int contextMenuWidth;
    private int contextMenuHeight;
    private boolean contextMenuVisible;
    private boolean mapConsumerReleased;

    private static volatile ColonyChunkMapElement activeInstance;

    public ColonyChunkMapElement(ColonyCoreOpenResponsePacket packet) {
        this.packet = packet;
        for (ColonyCoreOpenResponsePacket.ChunkCoord cc : packet.colonyChunks()) {
            selfChunks.add(ChunkPos.asLong(cc.x(), cc.z()));
        }
        activeInstance = this;
        mapManager.init();
        mapManager.acquireConsumer();
        forceInitialMapScan();
        centerMapOnColonyCore();
        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDragUpdate);
    }

    @Override
    protected void onRemoved() {
        if (activeInstance == this) activeInstance = null;
        releaseMapConsumer();
        super.onRemoved();
    }

    private void forceInitialMapScan() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        int chunkX = minecraft.player.chunkPosition().x;
        int chunkZ = minecraft.player.chunkPosition().z;
        mapManager.forceScanArea(chunkX, chunkZ, Math.min(12, mapManager.getEffectiveScanRadius()));
        mapManager.forceRenderAll();
    }

    private void releaseMapConsumer() {
        if (mapConsumerReleased) return;
        mapConsumerReleased = true;
        mapManager.releaseConsumer();
    }

    private void centerMapOnColonyCore() {
        double chunkSize = 16.0D * zoomLevel;
        int coreChunkX = packet.colonyCorePos().getX() >> 4;
        int coreChunkZ = packet.colonyCorePos().getZ() >> 4;
        offsetX = -coreChunkX * chunkSize;
        offsetY = -coreChunkZ * chunkSize;
    }

    @Override
    public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int canvasWidth = Math.round(getSizeWidth());
        int canvasHeight = Math.round(getSizeHeight());
        if (canvasWidth <= MAP_SIDE_PADDING * 2 || canvasHeight <= MAP_TOP_PADDING + MAP_SIDE_PADDING) return;
        guiContext.graphics.fill(x, y, x + canvasWidth, y + canvasHeight, 0x80000000);
        int mapStartX = x + MAP_SIDE_PADDING;
        int mapStartY = y + MAP_TOP_PADDING;
        int mapWidth = canvasWidth - MAP_SIDE_PADDING * 2;
        int mapHeight = canvasHeight - MAP_TOP_PADDING - MAP_SIDE_PADDING;
        guiContext.graphics.fill(mapStartX - 2, mapStartY - 2, mapStartX + mapWidth + 2, mapStartY + mapHeight + 2, 0xFFFFFFFF);
        guiContext.graphics.fill(mapStartX - 1, mapStartY - 1, mapStartX + mapWidth + 1, mapStartY + mapHeight + 1, 0x80000000);
        guiContext.graphics.flush();
        guiContext.enableScissor(mapStartX, mapStartY, mapWidth, mapHeight);
        renderMap(guiContext, mapStartX, mapStartY, mapWidth, mapHeight);
        guiContext.graphics.flush();
        guiContext.disableScissor();
    }

    private void renderMap(GUIContext guiContext, int startX, int startY, int width, int height) {
        double centerX = startX + width / 2.0D;
        double centerY = startY + height / 2.0D;
        double chunkSize = 16.0D * zoomLevel;
        int visibleChunksX = (int) Math.ceil(width / chunkSize) + 2;
        int visibleChunksY = (int) Math.ceil(height / chunkSize) + 2;
        int startChunkX = (int) Math.floor((-offsetX - width / 2.0D) / chunkSize);
        int startChunkZ = (int) Math.floor((-offsetY - height / 2.0D) / chunkSize);
        int endChunkX = startChunkX + visibleChunksX;
        int endChunkZ = startChunkZ + visibleChunksY;
        renderWorldMapTerrain(guiContext, startX, startY, width, height, centerX, centerY);
        renderGridOverlay(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, startChunkX, startChunkZ);
        renderHoveredChunk(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
        renderOwnedChunkBorders(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, startChunkX, endChunkX, startChunkZ, endChunkZ);
        renderCoreMarker(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
        renderHoverBox(guiContext, startX, startY, width, height, centerX, centerY, chunkSize);
        renderContextMenu(guiContext, startX, startY, width, height);
    }

    private void renderWorldMapTerrain(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY) {
        guiContext.graphics.fill(startX, startY, startX + width, startY + height, 0xFF1F1F1F);
        if (!SimuMapManager.isAvailable()) return;
        guiContext.graphics.flush();
        Collection<SimuMapRegion> regions = mapManager.getAllRegions();
        for (SimuMapRegion region : regions) {
            if (!region.isImageLoaded() && !region.hasData()) continue;
            int textureId = region.getTextureId();
            if (textureId == -1) continue;
            double regionWorldX = region.regionX * 512.0D;
            double regionWorldZ = region.regionZ * 512.0D;
            double screenX = centerX + offsetX + regionWorldX * zoomLevel;
            double screenY = centerY + offsetY + regionWorldZ * zoomLevel;
            double regionSize = 512.0D * zoomLevel;
            if (screenX + regionSize < startX || screenX > startX + width || screenY + regionSize < startY || screenY > startY + height) continue;
            drawRegionTexture(guiContext, textureId, screenX, screenY, regionSize);
        }
    }

    private void drawRegionTexture(GUIContext guiContext, int textureId, double screenX, double screenY, double regionSize) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f matrix = guiContext.graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float x0 = (float) screenX;
        float y0 = (float) screenY;
        float x1 = (float) (screenX + regionSize);
        float y1 = (float) (screenY + regionSize);
        buffer.addVertex(matrix, x0, y1, 0).setUv(0, 1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(1, 1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(1, 0);
        buffer.addVertex(matrix, x0, y0, 0).setUv(0, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void renderGridOverlay(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int startChunkX, int startChunkZ) {
        int verticalLines = (int) Math.ceil(width / chunkSize) + 2;
        int horizontalLines = (int) Math.ceil(height / chunkSize) + 2;
        for (int i = 0; i <= verticalLines; i++) {
            int drawX = (int) Math.round(centerX + offsetX + (startChunkX + i) * chunkSize);
            if (drawX >= startX && drawX <= startX + width) {
                guiContext.graphics.fill(drawX, startY, drawX + 1, startY + height, GRID_COLOR);
            }
        }
        for (int i = 0; i <= horizontalLines; i++) {
            int drawY = (int) Math.round(centerY + offsetY + (startChunkZ + i) * chunkSize);
            if (drawY >= startY && drawY <= startY + height) {
                guiContext.graphics.fill(startX, drawY, startX + width, drawY + 1, GRID_COLOR);
            }
        }
    }

    private void renderOwnedChunkBorders(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int startChunkX, int endChunkX, int startChunkZ, int endChunkZ) {
        UUID myParentCityId = packet.parentCityId();
        for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
            for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
                long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
                if (selfChunks.contains(chunkLong)) {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, SELF_CHUNK_FILL_COLOR);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, SELF_BORDER_COLOR);
                    continue;
                }
                UUID colonyOwner = colonyCache.getColonyOwner(chunkLong);
                if (colonyOwner != null) {
                    ColonyChunkClientCache.ColonyEntry entry = colonyCache.getColonyEntry(colonyOwner);
                    boolean isAlly = entry != null && entry.parentCityId() != null
                            && entry.parentCityId().equals(myParentCityId);
                    int fillColor = isAlly ? ALLY_CHUNK_FILL_COLOR : ENEMY_CHUNK_FILL_COLOR;
                    int borderColor = isAlly ? ALLY_BORDER_COLOR : ENEMY_BORDER_COLOR;
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, fillColor);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, borderColor);
                    continue;
                }
                if (cityCache.isChunkOwned(chunkLong)) {
                    boolean isAlly = cityCache.isChunkInCurrentCity(chunkLong);
                    int fillColor = isAlly ? ALLY_CHUNK_FILL_COLOR : ENEMY_CHUNK_FILL_COLOR;
                    int borderColor = isAlly ? ALLY_BORDER_COLOR : ENEMY_BORDER_COLOR;
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, fillColor);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, borderColor);
                    continue;
                }
                if (isAdjacentToSelf(chunkX, chunkZ)) {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, ADJACENT_BUYABLE_COLOR);
                }
            }
        }
    }

    private void drawChunkFill(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int fillColor) {
        double screenX = centerX + offsetX + chunkX * chunkSize;
        double screenY = centerY + offsetY + chunkZ * chunkSize;
        int drawX = Math.max((int) Math.floor(screenX), startX);
        int drawY = Math.max((int) Math.floor(screenY), startY);
        int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
        int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
        if (drawWidth > 0 && drawHeight > 0) {
            guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, fillColor);
        }
    }

    private void drawChunkOwnershipBorder(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int borderColor) {
        double screenX = centerX + offsetX + chunkX * chunkSize;
        double screenY = centerY + offsetY + chunkZ * chunkSize;
        int drawX = Math.max((int) Math.floor(screenX), startX);
        int drawY = Math.max((int) Math.floor(screenY), startY);
        int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
        int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
        if (drawWidth <= 0 || drawHeight <= 0) return;
        int borderThickness = Math.max(1, Math.min(2, drawWidth / 4));
        if (!isChunkOwned(chunkX, chunkZ - 1)) {
            guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + borderThickness, borderColor);
        }
        if (!isChunkOwned(chunkX, chunkZ + 1)) {
            guiContext.graphics.fill(drawX, drawY + drawHeight - borderThickness, drawX + drawWidth, drawY + drawHeight, borderColor);
        }
        if (!isChunkOwned(chunkX - 1, chunkZ)) {
            guiContext.graphics.fill(drawX, drawY, drawX + borderThickness, drawY + drawHeight, borderColor);
        }
        if (!isChunkOwned(chunkX + 1, chunkZ)) {
            guiContext.graphics.fill(drawX + drawWidth - borderThickness, drawY, drawX + drawWidth, drawY + drawHeight, borderColor);
        }
    }

    private boolean isChunkOwned(int chunkX, int chunkZ) {
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        return selfChunks.contains(chunkLong) || cityCache.isChunkOwned(chunkLong) || colonyCache.getColonyOwner(chunkLong) != null;
    }

    private boolean isAdjacentToSelf(int cx, int cz) {
        return selfChunks.contains(ChunkPos.asLong(cx + 1, cz))
                || selfChunks.contains(ChunkPos.asLong(cx - 1, cz))
                || selfChunks.contains(ChunkPos.asLong(cx, cz + 1))
                || selfChunks.contains(ChunkPos.asLong(cx, cz - 1));
    }

    private void renderCoreMarker(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
        int coreChunkX = packet.colonyCorePos().getX() >> 4;
        int coreChunkZ = packet.colonyCorePos().getZ() >> 4;
        double markerScreenX = centerX + offsetX + coreChunkX * chunkSize + (packet.colonyCorePos().getX() & 15) * zoomLevel;
        double markerScreenY = centerY + offsetY + coreChunkZ * chunkSize + (packet.colonyCorePos().getZ() & 15) * zoomLevel;
        if (markerScreenX >= startX && markerScreenX <= startX + width && markerScreenY >= startY && markerScreenY <= startY + height) {
            guiContext.graphics.fill((int) markerScreenX - 3, (int) markerScreenY - 3, (int) markerScreenX + 3, (int) markerScreenY + 3, 0xFF0000FF);
            guiContext.graphics.fill((int) markerScreenX - 2, (int) markerScreenY - 2, (int) markerScreenX + 2, (int) markerScreenY + 2, CORE_MARKER_COLOR);
        }
    }

    private void renderHoveredChunk(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        if (isOutside(mouseX, mouseY, startX, startY, width, height)) return;
        int chunkX = screenToChunk(mouseX, centerX, offsetX, chunkSize);
        int chunkZ = screenToChunk(mouseY, centerY, offsetY, chunkSize);
        double screenX = centerX + offsetX + chunkX * chunkSize;
        double screenY = centerY + offsetY + chunkZ * chunkSize;
        int drawX = Math.max((int) Math.floor(screenX), startX);
        int drawY = Math.max((int) Math.floor(screenY), startY);
        int drawWidth = Math.min((int) Math.ceil(screenX + chunkSize), startX + width) - drawX;
        int drawHeight = Math.min((int) Math.ceil(screenY + chunkSize), startY + height) - drawY;
        if (drawWidth > 0 && drawHeight > 0) {
            guiContext.graphics.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, 0x40FFFFFF);
        }
    }

    private void renderHoverBox(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize) {
        if (contextMenuVisible) return;
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        if (isOutside(mouseX, mouseY, startX, startY, width, height)) return;
        int chunkX = screenToChunk(mouseX, centerX, offsetX, chunkSize);
        int chunkZ = screenToChunk(mouseY, centerY, offsetY, chunkSize);
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        Component ownerText;
        if (selfChunks.contains(chunkLong)) {
            ownerText = Component.translatable("gui.xy2407_nsuk_addition.colony.map_self");
        } else if (cityCache.isChunkOwned(chunkLong) || colonyCache.getColonyOwner(chunkLong) != null) {
            ownerText = Component.translatable("gui.xy2407_nsuk_addition.colony.map_other");
        } else if (isAdjacentToSelf(chunkX, chunkZ)) {
            ownerText = Component.translatable("gui.xy2407_nsuk_addition.colony.map_buyable");
        } else {
            ownerText = Component.translatable("gui.xy2407_nsuk_addition.colony.map_unclaimed");
        }
        List<Component> lines = List.of(
                Component.translatable("gui.xy2407_nsuk_addition.colony.map_tooltip_chunk", chunkX, chunkZ),
                ownerText
        );
        int tooltipWidth = Math.max(minecraft.font.width(lines.get(0)), minecraft.font.width(lines.get(1)));
        int tooltipHeight = lines.size() * 10;
        int tooltipX = (int) mouseX + 10;
        int tooltipY = (int) mouseY - tooltipHeight - 8;
        if (tooltipX + tooltipWidth + 8 > startX + width) tooltipX = (int) mouseX - tooltipWidth - 12;
        if (tooltipY < startY) tooltipY = (int) mouseY + 12;
        tooltipX = Math.max(startX + 4, Math.min(tooltipX, startX + width - tooltipWidth - 8));
        tooltipY = Math.max(startY + 4, Math.min(tooltipY, startY + height - tooltipHeight - 8));
        guiContext.graphics.renderComponentTooltip(minecraft.font, lines, tooltipX, tooltipY);
    }

    private void renderContextMenu(GUIContext guiContext, int startX, int startY, int width, int height) {
        if (!contextMenuVisible) return;
        Minecraft minecraft = Minecraft.getInstance();
        long chunkLong = ChunkPos.asLong(contextMenuChunkX, contextMenuChunkZ);
        boolean isSelf = selfChunks.contains(chunkLong);
        boolean isOwned = isChunkOwned(contextMenuChunkX, contextMenuChunkZ);
        boolean canBuy = packet.canManageColony() && !isOwned && isAdjacentToSelf(contextMenuChunkX, contextMenuChunkZ);
        boolean canAbandon = packet.canManageColony() && isSelf;
        Component title = Component.translatable("gui.xy2407_nsuk_addition.colony.map_menu_title", contextMenuChunkX, contextMenuChunkZ);
        Component action;
        boolean actionEnabled = false;
        if (isSelf) {
            action = Component.translatable("gui.xy2407_nsuk_addition.colony.map_abandon");
            actionEnabled = canAbandon;
        } else if (!packet.canManageColony()) {
            action = Component.translatable("gui.xy2407_nsuk_addition.colony.map_no_permission");
        } else if (isOwned) {
            action = Component.translatable("gui.xy2407_nsuk_addition.colony.map_claim_unavailable");
        } else if (!isAdjacentToSelf(contextMenuChunkX, contextMenuChunkZ)) {
            action = Component.translatable("gui.xy2407_nsuk_addition.colony.map_not_adjacent");
        } else {
            action = Component.translatable("gui.xy2407_nsuk_addition.colony.map_buy");
            actionEnabled = canBuy;
        }
        contextMenuWidth = Math.max(minecraft.font.width(title), minecraft.font.width(action)) + 16;
        contextMenuHeight = 38;
        int menuX = (int) contextMenuX;
        int menuY = (int) contextMenuY;
        if (menuX + contextMenuWidth > startX + width) menuX = startX + width - contextMenuWidth;
        if (menuY + contextMenuHeight > startY + height) menuY = startY + height - contextMenuHeight;
        menuX = Math.max(startX + 2, menuX);
        menuY = Math.max(startY + 2, menuY);
        contextMenuDrawX = menuX;
        contextMenuDrawY = menuY;
        guiContext.graphics.fill(menuX, menuY, menuX + contextMenuWidth, menuY + contextMenuHeight, 0xEE202020);
        guiContext.graphics.fill(menuX, menuY, menuX + contextMenuWidth, menuY + 1, 0xFFFFFFFF);
        guiContext.graphics.fill(menuX, menuY + 19, menuX + contextMenuWidth, menuY + 20, 0x80FFFFFF);
        guiContext.graphics.drawString(minecraft.font, title, menuX + 6, menuY + 6, 0xFFFFFFFF, false);
        guiContext.graphics.drawString(minecraft.font, action, menuX + 6, menuY + 24, actionEnabled ? 0xFFFFFF55 : 0xFFAAAAAA, false);
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY) {
        if (!contextMenuVisible) return false;
        if (isOutside(mouseX, mouseY, contextMenuDrawX, contextMenuDrawY, contextMenuWidth, contextMenuHeight)) {
            contextMenuVisible = false;
            return true;
        }
        if (mouseY < contextMenuDrawY + 20) return true;
        contextMenuVisible = false;
        if (mouseY < contextMenuDrawY + 36) {
            long chunkLong = ChunkPos.asLong(contextMenuChunkX, contextMenuChunkZ);
            if (packet.canManageColony()) {
                if (selfChunks.contains(chunkLong)) {
                    PacketDistributor.sendToServer(new ColonyChunkAbandonPacket(packet.colonyId(), contextMenuChunkX, contextMenuChunkZ));
                } else if (!isChunkOwned(contextMenuChunkX, contextMenuChunkZ) && isAdjacentToSelf(contextMenuChunkX, contextMenuChunkZ)) {
                    PacketDistributor.sendToServer(new ColonyChunkBuyPacket(packet.colonyId(), contextMenuChunkX, contextMenuChunkZ));
                }
            }
        }
        return true;
    }

    private int screenToChunk(double screenValue, double centerValue, double offsetValue, double chunkSize) {
        return (int) Math.floor((screenValue - centerValue - offsetValue) / chunkSize);
    }

    private boolean isOutside(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height;
    }

    private void onMouseDown(UIEvent event) {
        if (contextMenuVisible && event.button == 0 && handleContextMenuClick(event.x, event.y)) {
            event.stopPropagation();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double mapStartX = getPositionX() + MAP_SIDE_PADDING;
        double mapStartY = getPositionY() + MAP_TOP_PADDING;
        double mapWidth = getSizeWidth() - MAP_SIDE_PADDING * 2.0D;
        double mapHeight = getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING;
        if (isOutside(event.x, event.y, mapStartX, mapStartY, mapWidth, mapHeight)) {
            contextMenuVisible = false;
            return;
        }
        if (event.button == 0) {
            event.target.startDrag(new Vector2f((float) offsetX, (float) offsetY), null);
            event.stopPropagation();
            return;
        }
        if (event.button == 1) {
            double centerX = mapStartX + mapWidth / 2.0D;
            double centerY = mapStartY + mapHeight / 2.0D;
            double chunkSize = 16.0D * zoomLevel;
            contextMenuChunkX = screenToChunk(event.x, centerX, offsetX, chunkSize);
            contextMenuChunkZ = screenToChunk(event.y, centerY, offsetY, chunkSize);
            contextMenuX = event.x;
            contextMenuY = event.y;
            contextMenuVisible = true;
            event.stopPropagation();
        }
    }

    private void onDragUpdate(UIEvent event) {
        contextMenuVisible = false;
        if (event.dragHandler.getDraggingObject() instanceof Vector2f startOffset) {
            offsetX = startOffset.x + event.x - event.dragStartX;
            offsetY = startOffset.y + event.y - event.dragStartY;
            event.stopPropagation();
        }
    }

    private void onMouseWheel(UIEvent event) {
        contextMenuVisible = false;
        double oldZoom = zoomLevel;
        if (event.deltaY > 0) {
            zoomLevel = Math.min(zoomLevel + ZOOM_STEP, MAX_ZOOM);
        } else {
            zoomLevel = Math.max(zoomLevel - ZOOM_STEP, MIN_ZOOM);
        }
        if (oldZoom != zoomLevel) {
            double mapStartX = getPositionX() + MAP_SIDE_PADDING;
            double mapStartY = getPositionY() + MAP_TOP_PADDING;
            double mapWidth = getSizeWidth() - MAP_SIDE_PADDING * 2.0D;
            double mapHeight = getSizeHeight() - MAP_TOP_PADDING - MAP_SIDE_PADDING;
            double centerX = mapStartX + mapWidth / 2.0D;
            double centerY = mapStartY + mapHeight / 2.0D;
            double mouseOffsetX = event.x - centerX;
            double mouseOffsetY = event.y - centerY;
            double scaleFactor = zoomLevel / oldZoom;
            offsetX = mouseOffsetX - (mouseOffsetX - offsetX) * scaleFactor;
            offsetY = mouseOffsetY - (mouseOffsetY - offsetY) * scaleFactor;
        }
        event.stopPropagation();
    }

    public static void onColonyChunkSync(UUID colonyId, List<ColonyCoreOpenResponsePacket.ChunkCoord> chunks) {
        ColonyChunkMapElement instance = activeInstance;
        if (instance == null || !colonyId.equals(instance.packet.colonyId())) return;
        instance.selfChunks.clear();
        for (ColonyCoreOpenResponsePacket.ChunkCoord cc : chunks) {
            instance.selfChunks.add(ChunkPos.asLong(cc.x(), cc.z()));
        }
    }

    public static void onColonyRemoved(UUID colonyId) {
        ColonyChunkMapElement instance = activeInstance;
        if (instance == null || !colonyId.equals(instance.packet.colonyId())) return;
        instance.selfChunks.clear();
    }
}
