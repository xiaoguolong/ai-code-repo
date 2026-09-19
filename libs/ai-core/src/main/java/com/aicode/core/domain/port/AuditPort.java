package com.aicode.core.domain.port;

import com.aicode.core.domain.model.TokenRecord;
import com.aicode.core.domain.model.TokenStats;

/**
 * 出站端口：Token 统计落库。每次模型调用后写入，供成本统计。
 */
public interface AuditPort {

    /**
     * 写入一条 Token 记录。失败路径也应尽量落库，便于统计故障成本。
     */
    void record(TokenRecord record);

    /**
     * 按用户汇总 Token 用量。
     *
     * @return 累计请求次数与 Token
     */
    TokenStats summaryByUser(long userId);
}
