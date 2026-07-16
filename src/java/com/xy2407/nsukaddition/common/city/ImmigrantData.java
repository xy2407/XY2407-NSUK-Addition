package com.xy2407.nsukaddition.common.city;

import java.util.UUID;

/** 移民请求数据，记录待入驻市民的基本信息与安置资金。 */
public record ImmigrantData(

        UUID requestId,

        UUID cityId,

        UUID citizenId,

        String name,

        double grantFunds,

        long createdDay
) {}
