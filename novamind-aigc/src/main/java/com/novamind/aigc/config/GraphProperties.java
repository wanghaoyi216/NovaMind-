package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.graph")
public class GraphProperties {

    private boolean enabled = false;

    /**
     * Neo4j HTTP endpoint, for example http://localhost:7474.
     */
    private String uri = "http://localhost:7474";

    private String username = "neo4j";

    private String password = "password";

    private String database = "neo4j";

    /**
     * Maximum graph facts appended to a prompt.
     */
    private int maxPromptFacts = 8;
}
