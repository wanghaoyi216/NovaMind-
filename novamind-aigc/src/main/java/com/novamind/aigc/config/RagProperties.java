package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.rag")
public class RagProperties {

    private double similarityThreshold = 0.6d;

    private int topK = 6;
}
