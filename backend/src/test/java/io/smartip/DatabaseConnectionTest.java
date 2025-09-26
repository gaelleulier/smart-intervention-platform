package io.smartip;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sip_db",
        "spring.datasource.username=sip_user",
        "spring.datasource.password=sip_password"
})
class DatabaseConnectionTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void canQueryDatabase() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }
}