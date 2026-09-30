package nguyen.vn;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import nguyen.vn.config.ApplicationConfiguration;
import nguyen.vn.config.SecurityConfiguration;
import nguyen.vn.controller.AuthController;
import nguyen.vn.controller.AuthenticationController;
import nguyen.vn.controller.UserController;
import nguyen.vn.entity.User;
import nguyen.vn.exception.GlobalExceptionHandler;
import nguyen.vn.filter.JwtAuthenticationFilter;
import nguyen.vn.repository.UserRepository;
import nguyen.vn.service.AuthenticationService;
import nguyen.vn.service.JwtService;
import nguyen.vn.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthenticationController.class, UserController.class, AuthController.class})
@Import({ApplicationConfiguration.class, SecurityConfiguration.class, JwtAuthenticationFilter.class,
        JwtService.class, AuthenticationService.class, UserService.class, GlobalExceptionHandler.class})
class AuthenticationIntegrationTests {
    private static final String EMAIL = "student@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @MockitoBean UserRepository users;
    @Value("${security.jwt.secret-key}") String secret;
    private User account;

    @BeforeEach
    void prepareUser() {
        account = new User();
        account.setId(1);
        account.setFullName("Nguyễn Văn An");
        account.setEmail(EMAIL);
        account.setPassword(encoder.encode("123456"));
        account.setImages("/images/profile.png");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(users.findAll()).thenReturn(List.of(account));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2);
            return saved;
        });
    }

    @Test
    void signupReturnsUserWithHashedPasswordAndSavesCorrectly() throws Exception {
        var response = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "new@example.com",
                                "password", "123456", "fullName", "New User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New User"))
                .andExpect(jsonPath("$.password").exists())
                .andExpect(jsonPath("$.username").value("new@example.com"))
                .andReturn();
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(saved.capture());
        assertThat(encoder.matches("123456", saved.getValue().getPassword())).isTrue();
    }

    @Test
    void loginCreatesJwtWith30HoursLifetime() throws Exception {
        String token = login("123456");
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(108000000L);
    }

    @Test
    void incorrectPasswordReturns401() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", EMAIL, "password", "wrong"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userApiRequiresBearerTokenOrReturns403() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isForbidden());
        mvc.perform(get("/users/")).andExpect(status().isForbidden());
        var result = mvc.perform(get("/users/me").header("Authorization", "Bearer " + jwt.generateToken(account)))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getContentAsString()).contains(EMAIL);
    }

    @Test
    void invalidTokenReturns500AndExpiredTokenReturns403() throws Exception {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        String expired = Jwts.builder().subject(EMAIL).expiration(new Date(1000)).signWith(key).compact();

        // Expired token throws ExpiredJwtException -> 403 Forbidden (slide 16)
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.description").value("The JWT token has expired"));

        // Malformed token falls into default handler -> 500 Internal Server Error (slide 17)
        mvc.perform(get("/users/me").header("Authorization", "Bearer a.b.c"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.description").value("Unknown internal server error."));
    }

    @Test
    void loginFormAndProfileAndStaticAssetsArePublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"login\"")));
        mvc.perform(get("/user/profile")).andExpect(status().isOk())
                .andExpect(content().string(containsString("SecureAuth")));
        mvc.perform(get("/js/mainjs.js")).andExpect(status().isOk());
        mvc.perform(get("/images/profile.png")).andExpect(status().isOk());
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    private String login(String password) throws Exception {
        var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", EMAIL, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(3600000))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
