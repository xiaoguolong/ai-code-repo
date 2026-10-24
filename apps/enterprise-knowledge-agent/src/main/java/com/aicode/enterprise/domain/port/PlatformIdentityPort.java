package com.aicode.enterprise.domain.port;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.enterprise.domain.exception.SsoUnavailableException;
import com.aicode.enterprise.domain.model.PlatformIdentity;

/**
 * 出站端口：委托平台校验凭据（Week 21 统一身份）。
 *
 * <p>只做「校验」，不创建/持有平台会话；实现类负责 HTTP 协议与超时。</p>
 */
public interface PlatformIdentityPort {

    /**
     * 校验凭据并返回平台身份。
     *
     * @param username 用户名
     * @param password 明文密码（只在内存中传递，不入日志）
     * @return 平台身份
     * @throws AuthenticationException 凭据错误（平台 401）
     * @throws SsoUnavailableException 身份源不可用（超时、5xx、报文异常）
     */
    PlatformIdentity authenticate(String username, String password);
}
