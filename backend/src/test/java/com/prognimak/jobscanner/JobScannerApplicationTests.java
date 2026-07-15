package com.prognimak.jobscanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=public",
        "spring.liquibase.parameters.db-schema=public",
        "spring.liquibase.parameters.text-type=VARCHAR(100000)",
        "spring.ai.openai.api-key=test"
})
class JobScannerApplicationTests {

    @Test
    void contextLoads() {
    }
}
