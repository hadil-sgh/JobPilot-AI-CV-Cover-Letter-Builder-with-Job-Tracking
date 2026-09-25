package com.jobpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SchemaAndHealthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void flywayCreatesFullSchemaWithPgvector() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "refresh_tokens", "profiles", "profile_items", "profile_chunks",
                "job_descriptions", "applications", "generated_documents", "generation_jobs", "email_accounts",
                "email_messages");

        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class))
                .isEqualTo(1);
        String embeddingType = jdbc.queryForObject(
                "select format_type(atttypid, atttypmod) from pg_attribute "
                        + "where attrelid = 'profile_chunks'::regclass and attname = 'embedding'", String.class);
        assertThat(embeddingType).isEqualTo("vector(768)");
    }

    @Test
    void healthIsPublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
