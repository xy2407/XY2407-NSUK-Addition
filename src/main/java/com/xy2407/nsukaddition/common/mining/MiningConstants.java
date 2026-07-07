package com.xy2407.nsukaddition.common.mining;

/** 挖矿系统常量定义，包含区域尺寸、深度限制与状态键名。 */
public final class MiningConstants {
    public static final String DATA_NAME = "nsukaddition_mining_boxes";
    public static final String HIRE_SOURCE_TYPE = "mining_control_box";
    public static final String HIRE_ROLE = "miner";

    public static final int MINE_AREA_SIZE = 16;

    public static final int TICKS_PER_LAYER = 1200;

    public static final int MIN_DEPTH = -64;

    public static final int CONTAINER_SEARCH_RADIUS = 5;

    public static final String STATUS_IDLE = "gui.xy2407_nsuk_addition.mining.status.idle";
    public static final String STATUS_RUNNING = "gui.xy2407_nsuk_addition.mining.status.running";
    public static final String STATUS_NO_WORKER = "gui.xy2407_nsuk_addition.mining.status.no_worker";
    public static final String STATUS_NO_CONTAINER = "gui.xy2407_nsuk_addition.mining.status.no_container";
    public static final String STATUS_CONTAINER_FULL = "gui.xy2407_nsuk_addition.mining.status.container_full";
    public static final String STATUS_MAX_DEPTH = "gui.xy2407_nsuk_addition.mining.status.max_depth";
    public static final String STATUS_PAUSED = "gui.xy2407_nsuk_addition.mining.status.paused";
    public static final String STATUS_WORKER_FIRED = "gui.xy2407_nsuk_addition.mining.status.worker_fired";
    public static final String STATUS_INTERRUPTED = "gui.xy2407_nsuk_addition.mining.status.interrupted";
    public static final String STATUS_NO_PICKAXE = "gui.xy2407_nsuk_addition.mining.status.no_pickaxe";

    private MiningConstants() {}
}
