package nguyen.vn.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import nguyen.vn.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private static final String SECRET = "3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b";

    private JwtService service() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", SECRET);
        ReflectionTestUtils.setField(service, "jwtExpiration", 3600000L);
        return service;
    }

    @Test
    void fixedKeyAllowsTokenAfterServiceRestart() {
        User user = new User();
        user.setEmail("student@example.com");
        String token = service().generateToken(user);
        assertThat(service().isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenCannotAuthenticateADifferentUser() {
        User user = new User();
        user.setEmail("student@example.com");
        JwtService jwt = service();
        String token = jwt.generateToken(user);
        user.setEmail("other@example.com");
        assertThat(jwt.isTokenValid(token, user)).isFalse();
    }

    @Test
    void expiredTokenIsRejectedWithoutWaiting() {
        String expired = Jwts.builder().subject("student@example.com").expiration(new Date(1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
        assertThatThrownBy(() -> service().extractUsername(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
