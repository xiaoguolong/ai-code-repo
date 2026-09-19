package com.aicode.enterprise.application;

import com.aicode.core.domain.port.AuthTokenPort;
import org.springframework.stereotype.Service;

/**
 * 注销用例：注销当前登录态。
 */
@Service
public class LogoutUseCase {

    private final AuthTokenPort authTokenPort;

    public LogoutUseCase(AuthTokenPort authTokenPort) {
        this.authTokenPort = authTokenPort;
    }

    /**
     * 注销当前登录态。
     */
    public void logout() {
        authTokenPort.logout();
    }
}
