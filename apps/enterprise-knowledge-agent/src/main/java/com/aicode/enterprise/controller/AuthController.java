package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.GetCurrentUserUseCase;
import com.aicode.enterprise.application.LoginUserCommand;
import com.aicode.enterprise.application.LoginUserOutcome;
import com.aicode.enterprise.application.LoginUserUseCase;
import com.aicode.enterprise.application.LogoutUseCase;
import com.aicode.enterprise.application.RegisterUserCommand;
import com.aicode.enterprise.application.RegisterUserOutcome;
import com.aicode.enterprise.application.RegisterUserUseCase;
import com.aicode.enterprise.domain.model.User;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.AuthResponse;
import com.aicode.enterprise.dto.LoginRequest;
import com.aicode.enterprise.dto.RegisterRequest;
import com.aicode.enterprise.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接入。只做校验与协议转换，鉴权逻辑在用例层。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            LoginUserUseCase loginUserUseCase,
            LogoutUseCase logoutUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.logoutUseCase = logoutUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    /**
     * 注册。
     *
     * @return 201 带 user；重名 409
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        RegisterUserOutcome outcome = registerUserUseCase.register(
                new RegisterUserCommand(request.username(), request.password())
        );
        return ApiResponse.success(new UserDto(outcome.userId(), outcome.username()));
    }

    /**
     * 登录。
     *
     * @return 200 带 token 与 user；失败 401
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginUserOutcome outcome = loginUserUseCase.login(
                new LoginUserCommand(request.username(), request.password())
        );
        return ApiResponse.success(new AuthResponse(outcome.token(), new UserDto(outcome.userId(), outcome.username())));
    }

    /**
     * 注销当前登录态。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        logoutUseCase.logout();
        return ApiResponse.success(null);
    }

    /**
     * 当前登录用户。
     */
    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = getCurrentUserUseCase.get(userId);
        return ApiResponse.success(new UserDto(user.id(), user.username()));
    }
}
