package com.aicode.gateway.infrastructure.redis;

import com.aicode.gateway.domain.model.GatewaySession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话编解码测试（Week 19）。
 *
 * <p>为什么值得单独测：会话值里含平台 token（凭据），编解码出错有两种后果 ——
 * 要么「所有在线会话立即失效」（字段不兼容），要么「读出一个空 token 的会话」
 * （鉴权被绕过）。两种都不能靠人工点页面发现。</p>
 */
class GatewaySessionCodecTest {

    private final GatewaySessionCodec codec = new GatewaySessionCodec(new ObjectMapper());

    @Test
    @DisplayName("编解码往返：字段逐个还原")
    void roundTripKeepsAllFields() {
        GatewaySession session = new GatewaySession("sk-platform-1", 7L, "admin", "ADMIN", 1893456000L);
        GatewaySession decoded = codec.decode(codec.encode(session));

        assertThat(decoded).isNotNull();
        assertThat(decoded.platformToken()).isEqualTo("sk-platform-1");
        assertThat(decoded.userId()).isEqualTo(7L);
        assertThat(decoded.username()).isEqualTo("admin");
        assertThat(decoded.roleKey()).isEqualTo("ADMIN");
        assertThat(decoded.expiresAtEpochSecond()).isEqualTo(1893456000L);
    }

    @Test
    @DisplayName("向后兼容：旧格式缺 username/roleKey 时按空串补齐，token 仍在")
    void decodeToleratesMissingFields() {
        GatewaySession decoded = codec.decode(
                "{\"platformToken\":\"sk-old\",\"userId\":3,\"expiresAtEpochSecond\":100}");

        assertThat(decoded).isNotNull();
        assertThat(decoded.platformToken()).isEqualTo("sk-old");
        assertThat(decoded.username()).isEmpty();
        assertThat(decoded.roleKey()).isEmpty();
        assertThat(decoded.userId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("损坏 / 空 / 缺 token 一律返回 null（调用方视为未登录，不静默放行）")
    void decodeRejectsBrokenValues() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("   ")).isNull();
        assertThat(codec.decode("not-json")).isNull();
        assertThat(codec.decode("{\"userId\":1}")).isNull();
        assertThat(codec.decode("{\"platformToken\":\"  \"}")).isNull();
    }

    @Test
    @DisplayName("过期判定：等于过期时间即视为过期")
    void expiredAtIsInclusive() {
        GatewaySession session = new GatewaySession("t", 1L, "u", "r", 100L);
        assertThat(session.expiredAt(99L)).isFalse();
        assertThat(session.expiredAt(100L)).isTrue();
        assertThat(session.expiredAt(101L)).isTrue();
    }

    @Test
    @DisplayName("过期时间为 0 的会话永不判过期（老数据兜底，交给 TTL 处理）")
    void zeroExpiryNeverExpires() {
        GatewaySession session = new GatewaySession("t", 1L, "u", "r", 0L);
        assertThat(session.expiredAt(Long.MAX_VALUE)).isFalse();
    }

    @Test
    @DisplayName("序列化失败抛 IllegalStateException（由适配器兜底，不产生半截值）")
    void encodeFailureIsExplicit() {
        GatewaySessionCodec broken = new GatewaySessionCodec(new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("boom") { };
            }
        });
        assertThatThrownBy(() -> broken.encode(new GatewaySession("t", 1L, "u", "r", 1L)))
                .isInstanceOf(IllegalStateException.class);
    }
}
