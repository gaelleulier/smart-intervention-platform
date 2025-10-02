package io.smartip.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerIntegrationTest {

    private static final String EMAIL = "auth.user@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                "INSERT INTO users (email, full_name, role, password_hash) VALUES (?,?,?,?)",
                EMAIL,
                "Auth User",
                "ADMIN",
                passwordEncoder.encode(PASSWORD));
    }

    @Test
    void login_returnsToken_whenCredentialsValid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void login_rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of("email", EMAIL, "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_updatesHash_andRequiresReAuthentication() throws Exception {
        String token = obtainToken(PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", "NewPassword456!"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of("email", EMAIL, "password", "NewPassword456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void changePassword_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", "NewPassword456!"))))
                .andExpect(status().isUnauthorized());
    }

    private String obtainToken(String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(Map.of("email", EMAIL, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String asJson(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}
