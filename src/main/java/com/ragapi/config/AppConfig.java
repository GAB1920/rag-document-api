package com.ragapi.config;

import com.pgvector.PGvector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class AppConfig {

    /**
     * WebClient pre-configured for OpenAI API calls.
     */
    @Bean
    public WebClient openAiWebClient(OpenAiProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Register pgvector type with the PostgreSQL JDBC driver at startup.
     */
    @Bean
    public Boolean pgVectorSetup(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
        }
        return true;
    }
}
