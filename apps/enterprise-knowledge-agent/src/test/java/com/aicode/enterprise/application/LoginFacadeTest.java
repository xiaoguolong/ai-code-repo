package com.aicode.enterprise.application;

import com.aicode.enterprise.infrastructure.sso.SsoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 登录路由：开关决定走本地还是平台身份源。 */
class LoginFacadeTest {

    @Test
    void usesSsoWhenEnabled() {
        RecordingLocal local = new RecordingLocal();
        RecordingSso sso = new RecordingSso();
        LoginFacade facade = new LoginFacade(local, sso, new SsoProperties(true, "http://localhost:8084", 3000, true));

        LoginUserOutcome outcome = facade.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.token()).isEqualTo("sso-token");
        assertThat(sso.calls).isEqualTo(1);
        assertThat(local.calls).isZero();
    }

    @Test
    void usesLocalWhenDisabled() {
        RecordingLocal local = new RecordingLocal();
        RecordingSso sso = new RecordingSso();
        LoginFacade facade = new LoginFacade(local, sso, new SsoProperties(false, "http://localhost:8084", 3000, true));

        LoginUserOutcome outcome = facade.login(new LoginUserCommand("alice", "pass123"));

        assertThat(outcome.token()).isEqualTo("local-token");
        assertThat(local.calls).isEqualTo(1);
        assertThat(sso.calls).isZero();
    }

    /** 只记录是否被调用，不关心内部逻辑。 */
    private static class RecordingLocal extends LoginUserUseCase {
        private int calls;

        RecordingLocal() {
            super(null, null, null);
        }

        @Override
        public LoginUserOutcome login(LoginUserCommand command) {
            calls++;
            return new LoginUserOutcome("local-token", 1L, command.username());
        }
    }

    private static class RecordingSso extends SsoLoginUseCase {
        private int calls;

        RecordingSso() {
            super(null, null, null, null, null, new SsoProperties(true, "", 3000, true), null);
        }

        @Override
        public LoginUserOutcome login(LoginUserCommand command) {
            calls++;
            return new LoginUserOutcome("sso-token", 2L, command.username());
        }
    }
}
