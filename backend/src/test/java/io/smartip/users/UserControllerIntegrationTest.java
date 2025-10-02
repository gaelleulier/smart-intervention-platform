package io.smartip.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void list_returnsEmptyPage_whenNoUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void list_supportsPaginationAndSorting() throws Exception {
        for (int i = 0; i < 5; i++) {
            createUser("user%02d@example.com".formatted(i), "User %02d".formatted(i), null);
        }

        mockMvc.perform(get("/api/users?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("user02@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("user03@example.com"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void list_filtersByRole() throws Exception {
        createUser("admin@example.com", "Admin User", "ADMIN");
        createUser("tech@example.com", "Tech User", null);

        mockMvc.perform(get("/api/users?role=ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin@example.com"));
    }

    @Test
    void list_filtersBySearchQuery() throws Exception {
        createUser("alice@example.com", "Alice Wonderland", "DISPATCHER");
        createUser("bob@example.com", "Bob Builder", null);

        mockMvc.perform(get("/api/users?query=ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    @Test
    void list_filtersByRoleAndQueryTogether() throws Exception {
        createUser("alice@example.com", "Alice Admin", "ADMIN");
        createUser("alice.tech@example.com", "Alice Tech", null);

        mockMvc.perform(get("/api/users?role=ADMIN&query=alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    @Test
    void list_rejectsInvalidRoleParameter() throws Exception {
        mockMvc.perform(get("/api/users?role=UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_persistsUser_andReturnsResource() throws Exception {
        Long userId = createUser("john.doe@example.com", "John Doe", "DISPATCHER");
        assertThat(userId).isEqualTo(1L);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void create_defaultsRoleToTech_whenMissing() throws Exception {
        String payload = asJson(Map.of(
                "email", "jane.doe@example.com",
                "fullName", "Jane Doe"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TECH"));
    }

    @Test
    void create_rejectsDuplicateEmail_caseInsensitive() throws Exception {
        createUser("unique@example.com", "First User", null);

        String duplicate = asJson(Map.of(
                "email", "Unique@example.com",
                "fullName", "Second User"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isConflict());
    }

    @Test
    void create_rejectsInvalidRole() throws Exception {
        String payload = asJson(Map.of(
                "email", "invalid.role@example.com",
                "fullName", "Invalid Role",
                "role", "MANAGER"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_returnsUser_whenExists() throws Exception {
        Long userId = createUser("get.user@example.com", "Get User", "TECH");

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("get.user@example.com"));
    }

    @Test
    void get_returnsNotFound_whenUserMissing() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_replacesAllFields() throws Exception {
        Long userId = createUser("update.user@example.com", "Update User", null);

        String payload = asJson(Map.of(
                "email", "updated@example.com",
                "fullName", "Updated User",
                "role", "ADMIN"));

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.fullName").value("Updated User"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void update_rejectsDuplicateEmail() throws Exception {
        createUser("existing@example.com", "Existing User", null);
        Long userId = createUser("second@example.com", "Second User", "TECH");

        String payload = asJson(Map.of(
                "email", "Existing@example.com",
                "fullName", "Second User",
                "role", "TECH"));

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void update_rejectsInvalidRole() throws Exception {
        Long userId = createUser("role.check@example.com", "Role Check", null);

        String payload = asJson(Map.of(
                "email", "role.check@example.com",
                "fullName", "Role Check",
                "role", "SUPERVISOR"));

        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_removesUser() throws Exception {
        Long userId = createUser("delete.user@example.com", "Delete User", null);

        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returnsNotFound_whenUserMissing() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 12345))
                .andExpect(status().isNotFound());
    }

    private Long createUser(String email, String fullName, String role) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("fullName", fullName);
        if (role != null) {
            payload.put("role", role);
        }

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private String asJson(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}
