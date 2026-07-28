package com.xy2407.nsukaddition.common.city;

import java.util.UUID;

/** 住宅建筑租金条目：建筑ID、城市ID、租金金额，用于收租反向索引。 */
public record RentEntry(UUID buildingId, UUID cityId, double rent) {}
