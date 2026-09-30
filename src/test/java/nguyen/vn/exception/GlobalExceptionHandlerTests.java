package nguyen.vn.exception;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void lockedAccountReturns403() {
        assertThat(handler.handleSecurityException(new LockedException("locked")).getStatus()).isEqualTo(403);
    }

    @Test
    void accessDeniedReturns403() {
        assertThat(handler.handleSecurityException(new AccessDeniedException("denied")).getStatus()).isEqualTo(403);
    }
}
