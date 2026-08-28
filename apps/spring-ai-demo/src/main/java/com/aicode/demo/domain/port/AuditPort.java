package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.ChatAuditRecord;
import com.aicode.demo.domain.model.TokenStats;

/**
 * 出站端口：请求日志与 Token 累计。第1周内存实现，第2周可换仓储。
 */
public interface AuditPort {

    /**
     * 写入一条审计。失败路径也必须调用。
     */
    void record(ChatAuditRecord record);

    /**
     * 返回进程启动以来的累计 Token。
     */
    TokenStats summary();
}
