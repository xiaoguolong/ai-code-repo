package com.aicode.framework.platform.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端 IP 解析：默认只信 remoteAddr；只有显式信任反代时才读 X-Forwarded-For 第一跳。
 */
class ClientIpResolverTest {

    @Test
    void usesRemoteAddrByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");

        assertThat(ClientIpResolver.resolve(request, false)).isEqualTo("10.0.0.8");
    }

    @Test
    void usesFirstForwardedHopWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", " 1.2.3.4 , 5.6.7.8 ");

        assertThat(ClientIpResolver.resolve(request, true)).isEqualTo("1.2.3.4");
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "  ");

        assertThat(ClientIpResolver.resolve(request, true)).isEqualTo("10.0.0.8");
    }

    @Test
    void returnsUnknownWhenNothingAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(ClientIpResolver.resolve(request, false)).isEqualTo("unknown");
    }
}
