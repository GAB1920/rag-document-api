package com.ragapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class DocumentApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                           .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ragdb_test")
            .withUsername("raguser")
            .withPassword("ragpassword")
            .withInitScript("db/migration/V1__init_schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Use a fake key so OpenAI calls fail fast in tests
        registry.add("openai.api-key", () -> "test-key-not-real");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void listDocumentsReturnsEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.total").value(0))
               .andExpect(jsonPath("$.documents").isArray());
    }

    @Test
    void uploadEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents").file(empty))
               .andExpect(status().isBadRequest());
    }

    @Test
    void uploadTextDocumentReturnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", MediaType.TEXT_PLAIN_VALUE,
                "This is a test document with some content.".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file))
               .andExpect(status().isAccepted())
               .andExpect(jsonPath("$.id").exists())
               .andExpect(jsonPath("$.fileName").value("test.txt"))
               .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getUnknownDocumentReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/documents/00000000-0000-0000-0000-000000000000"))
               .andExpect(status().isNotFound());
    }

    @Test
    void askWithBlankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/qa/ask")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("""
                               {"question": ""}
                               """))
               .andExpect(status().isBadRequest());
    }

    @Test
    void historyEndpointIsReachable() throws Exception {
        mockMvc.perform(get("/api/v1/qa/history"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }
}
