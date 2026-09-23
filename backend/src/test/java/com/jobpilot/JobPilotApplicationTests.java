package com.jobpilot;

import org.junit.jupiter.api.Test;

class JobPilotApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Flyway migrations run against the pgvector Testcontainer; if the context
        // fails to start, the schema or bean wiring is broken.
    }
}
