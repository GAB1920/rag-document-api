package com.ragapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAiProperties {

    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String embeddingModel = "text-embedding-3-small";
    private int embeddingDimensions = 1536;
    private String chatModel = "gpt-4o";
    private int maxTokens = 2048;
    private double temperature = 0.2;
}
