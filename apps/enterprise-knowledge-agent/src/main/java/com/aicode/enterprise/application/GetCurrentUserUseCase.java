package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.model.User;
import com.aicode.enterprise.domain.port.UserPort;
import org.springframework.stereotype.Service;

/**
 * 查询当前用户用例。
 */
@Service
public class GetCurrentUserUseCase {

    private final UserPort userPort;

    public GetCurrentUserUseCase(UserPort userPort) {
        this.userPort = userPort;
    }

    /**
     * @throws NotFoundException 用户不存在
     */
    public User get(Long userId) {
        return userPort.findById(userId)
                .orElseThrow(() -> new NotFoundException("user not found"));
    }
}
