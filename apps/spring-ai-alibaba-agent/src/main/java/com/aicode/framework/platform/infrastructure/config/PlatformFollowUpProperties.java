package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 随访任务配置（Week 24，{@code platform.followup.*}）。
 */
@ConfigurationProperties(prefix = "platform.followup")
public class PlatformFollowUpProperties {

    /** 站内提醒默认天数。 */
    private int defaultReminderDays = 7;
    /** 站内提醒最大天数。 */
    private int maxReminderDays = 30;
    /** 列表默认条数。 */
    private int defaultListLimit = 20;
    /** 列表硬上限。 */
    private int maxListLimit = 100;

    /** @return 默认提醒天数（夹在 1–max） */
    public int resolvedDefaultReminderDays() {
        int days = defaultReminderDays > 0 ? defaultReminderDays : 7;
        return Math.min(days, resolvedMaxReminderDays());
    }

    /** @return 最大提醒天数（小于 1 时回落 30） */
    public int resolvedMaxReminderDays() {
        return maxReminderDays > 0 ? maxReminderDays : 30;
    }

    /** @return 默认列表条数 */
    public int resolvedDefaultListLimit() {
        return defaultListLimit > 0 ? defaultListLimit : 20;
    }

    /** @return 列表硬上限 */
    public int resolvedMaxListLimit() {
        return maxListLimit > 0 ? maxListLimit : 100;
    }

    public int getDefaultReminderDays() {
        return defaultReminderDays;
    }

    public void setDefaultReminderDays(int defaultReminderDays) {
        this.defaultReminderDays = defaultReminderDays;
    }

    public int getMaxReminderDays() {
        return maxReminderDays;
    }

    public void setMaxReminderDays(int maxReminderDays) {
        this.maxReminderDays = maxReminderDays;
    }

    public int getDefaultListLimit() {
        return defaultListLimit;
    }

    public void setDefaultListLimit(int defaultListLimit) {
        this.defaultListLimit = defaultListLimit;
    }

    public int getMaxListLimit() {
        return maxListLimit;
    }

    public void setMaxListLimit(int maxListLimit) {
        this.maxListLimit = maxListLimit;
    }
}
