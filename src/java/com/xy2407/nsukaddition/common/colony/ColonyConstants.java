package com.xy2407.nsukaddition.common.colony;

/** 附属领地系统常量，包含消息键、区块配额和功能参数。 */
public final class ColonyConstants {

    private ColonyConstants() {}

    public static final String MOD_ID = "xy2407_nsuk_addition";

    public static final String MSG_NO_CITY = "message.xy2407_nsuk_addition.colony.no_city";
    public static final String MSG_LEVEL_TOO_LOW = "message.xy2407_nsuk_addition.colony.level_too_low";
    public static final String MSG_COLONY_LIMIT = "message.xy2407_nsuk_addition.colony.limit_reached";
    public static final String MSG_CHUNK_POOL_EMPTY = "message.xy2407_nsuk_addition.colony.chunk_pool_empty";
    public static final String MSG_POS_ALREADY_CLAIMED = "message.xy2407_nsuk_addition.colony.pos_already_claimed";
    public static final String MSG_CREATE_FAILED = "message.xy2407_nsuk_addition.colony.create_failed";
    public static final String MSG_CREATE_SUCCESS = "message.xy2407_nsuk_addition.colony.create_success";
    public static final String MSG_DESTROYED = "message.xy2407_nsuk_addition.colony.destroyed";
    public static final String MSG_TOO_FAR = "message.xy2407_nsuk_addition.colony.too_far";
    public static final String MSG_MOVE_SUCCESS = "message.xy2407_nsuk_addition.colony.move_success";
    public static final String MSG_MOVE_NOT_IN_TERRITORY = "message.xy2407_nsuk_addition.colony.move_not_in_territory";
    public static final String MSG_MOVE_SAME_POS = "message.xy2407_nsuk_addition.colony.move_same_pos";
    public static final String MSG_MOVE_POS_OCCUPIED = "message.xy2407_nsuk_addition.colony.move_pos_occupied";
    public static final String MSG_MOVE_NOT_FOUND = "message.xy2407_nsuk_addition.colony.move_not_found";

    public static final String MSG_RELOCATE_SUCCESS = "message.xy2407_nsuk_addition.colony.relocate_success";
    public static final String MSG_RELOCATE_COLONY_NOT_FOUND = "message.xy2407_nsuk_addition.colony.relocate_colony_not_found";
    public static final String MSG_RELOCATE_CITIZEN_NOT_FOUND = "message.xy2407_nsuk_addition.colony.relocate_citizen_not_found";
    public static final String MSG_RELOCATE_ALREADY_HERE = "message.xy2407_nsuk_addition.colony.relocate_already_here";

    public static final int INITIAL_CLAIM_RADIUS = 1;

    public static final String HIRE_SOURCE_TYPE = "colony_core";
    public static final String HIRE_ROLE = "colony_worker";

    public static int maxColonies(int cityLevel) {
        return switch (cityLevel) {
            case 3 -> 1;
            case 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }

    public static int totalChunkPool(int cityLevel) {
        return switch (cityLevel) {
            case 3 -> 16;
            case 4 -> 50;
            case 5 -> 147;
            default -> 0;
        };
    }
}
