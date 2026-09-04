package com.aicode.enterprise.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件访问路径组装单元测试。
 */
class FileUrlAssemblerTest {

    @Test
    void buildsAccessUrl() {
        FileUrlAssembler assembler = new FileUrlAssembler("http://localhost:8081");

        assertThat(assembler.url("fid-1")).isEqualTo("http://localhost:8081/api/v1/files/fid-1");
    }

    @Test
    void stripsTrailingSlash() {
        FileUrlAssembler assembler = new FileUrlAssembler("http://localhost:8081/");

        assertThat(assembler.url("fid-2")).isEqualTo("http://localhost:8081/api/v1/files/fid-2");
    }
}
