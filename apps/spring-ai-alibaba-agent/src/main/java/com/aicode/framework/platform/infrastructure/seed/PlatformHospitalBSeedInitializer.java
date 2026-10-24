package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 第二租户演示用户（Week 21）。
 *
 * <p>单独一个初始化器而不是塞进 {@link PlatformRbacSeedInitializer}：后者以「用户表是否为空」
 * 为整体开关，升级到第 21 周的老库已有 admin/operator/viewer，会导致新增的 {@code doctor_b}
 * 永远建不出来（真库验证时实测：{@code platform_user} 只有 3 行）。
 * 本类按用户 ID 判空，老库/新库都能补齐。</p>
 *
 * <p>它存在的意义是**证明跨租户隔离真的生效**：没有第二个租户的真实用户，
 * 隔离测试只能证明「按用户隔离」。</p>
 */
@Component
public class PlatformHospitalBSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformHospitalBSeedInitializer.class);

    /** 第二租户用户 ID（避开第 16 周单测固定使用的 1–4 号）。 */
    public static final long HOSPITAL_B_USER_ID = 104L;

    private final PlatformUserPort userPort;
    private final PasswordHasher passwordHasher;

    public PlatformHospitalBSeedInitializer(PlatformUserPort userPort, PasswordHasher passwordHasher) {
        this.userPort = userPort;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userPort.findById(HOSPITAL_B_USER_ID).isPresent()) {
            return;
        }
        String hash = passwordHasher.hash("doctor123", "doctor_b");
        userPort.save(new PlatformUser(HOSPITAL_B_USER_ID, "doctor_b", hash, "operator", "hospital-b"));
        log.info("[platform] hospital-b seed applied userId={} tenantKey=hospital-b", HOSPITAL_B_USER_ID);
    }
}
