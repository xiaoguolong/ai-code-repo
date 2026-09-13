package com.aicode.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第5周 patient-agent 启动类。无状态 ReAct Agent：用户任务 → 思考-行动-观察循环 → 最终答案与轨迹。
 */
@SpringBootApplication
public class PatientAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientAgentApplication.class, args);
    }
}
